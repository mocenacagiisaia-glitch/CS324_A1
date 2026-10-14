package edu.usp.cs324.client.progress;

import edu.usp.cs324.api.Job;
import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Scripted component fixtures, not gRPC or distributed recovery tests. */
class JobMonitorTest {
    private static final Job INPUT = new Job(Job.Type.MAX, List.of(1, 9), 0, 0);
    private static final List<Duration> FAST = List.of(Duration.ZERO, Duration.ZERO);
    static JobSnapshot running(long revision, long attempt, int worker, long completed) {
        return new JobSnapshot("stable-job", revision, JobSnapshot.State.RUNNING, 10, completed,
                List.of(new JobSnapshot.Task("task-a", attempt, worker, 4, completed),
                        new JobSnapshot.Task("task-b", 1, 2, 6, 0)), null, null);
    }
    static JobSnapshot done(long revision) {
        return new JobSnapshot("stable-job", revision, JobSnapshot.State.COMPLETED, 10, 10,
                List.of(new JobSnapshot.Task("task-a", 2, 3, 4, 4),
                        new JobSnapshot.Task("task-b", 1, 2, 6, 6)), BigInteger.valueOf(9), null);
    }
    private static class Fixture implements JobClient {
        int submits, lookups, gets, watches, locates;
        final List<String> ids = new ArrayList<>();
        final List<String> keys = new ArrayList<>();
        @Override public void locateCoordinator() throws Unavailable { locates++; }
        @Override public JobSnapshot submit(String key, Job input) throws Unavailable {
            submits++; keys.add(key); return running(0, 1, 1, 0);
        }
        @Override public JobSnapshot lookupSubmission(String key) throws Unavailable, NotFound {
            lookups++; keys.add(key); return running(0, 1, 1, 0);
        }
        @Override public JobSnapshot getJob(String id) throws Unavailable, NotFound {
            gets++; ids.add(id); return running(0, 1, 1, 0);
        }
        @Override public void watchJob(String id, Consumer<JobSnapshot> updates)
                throws Unavailable, NotFound, InterruptedException {
            watches++; ids.add(id); updates.accept(done(2));
        }
        @Override public void close() { }
    }
    private JobMonitor monitor(Fixture service, List<JobMonitor.Update> updates) {
        return new JobMonitor(service, INPUT, "original-key", null, updates::add, FAST);
    }
    @Test void disconnectRediscoversAndResumesSameIdWithoutSubmittingAgain() {
        List<JobMonitor.Update> events = new ArrayList<>();
        Fixture service = new Fixture() {
            @Override public void watchJob(String id, Consumer<JobSnapshot> updates) throws Unavailable {
                watches++; ids.add(id);
                if (watches == 1) {
                    updates.accept(running(1, 2, 3, 4));
                    throw new Unavailable("stream disconnected");
                }
                updates.accept(done(2));
            }
        };
        monitor(service, events).run();
        assertEquals(1, service.submits);
        assertEquals(2, service.locates);
        assertTrue(service.ids.stream().allMatch("stable-job"::equals));
        var reconnect = events.stream().filter(e -> e.connection() == JobMonitor.Connection.RECONNECTING)
                .findFirst().orElseThrow();
        assertEquals("stable-job", reconnect.jobId());
        assertEquals(40, reconnect.snapshot().percent());
        assertEquals(JobMonitor.Connection.FINISHED, events.getLast().connection());
        assertEquals(BigInteger.valueOf(9), events.getLast().snapshot().result());
    }
    @Test void replayAndOldSnapshotsNeverAddCreditsAndNewAttemptReplacesWorker() {
        List<JobMonitor.Update> events = new ArrayList<>();
        Fixture service = new Fixture() {
            @Override public void watchJob(String id, Consumer<JobSnapshot> updates) {
                var next = running(1, 2, 3, 4);
                updates.accept(next); updates.accept(next);
                updates.accept(running(0, 1, 1, 4));
                updates.accept(done(2));
            }
        };
        monitor(service, events).run();
        assertEquals(1, events.stream().filter(e -> e.snapshot() != null && e.snapshot().revision() == 1).count());
        assertEquals(Map.of(3, 4L, 2, 0L), events.stream()
                .filter(e -> e.snapshot() != null && e.snapshot().revision() == 1)
                .findFirst().orElseThrow().snapshot().workerContributions());
        assertEquals(10, events.getLast().snapshot().completedUnits());
    }
    @Test void successfulReconnectRestoresMonitoringEvenIfProgressDidNotChange() {
        List<JobMonitor.Update> events = new ArrayList<>();
        Fixture service = new Fixture() {
            @Override public void watchJob(String id, Consumer<JobSnapshot> updates) throws Unavailable {
                if (++watches == 1) throw new Unavailable("disconnect with no progress");
                assertEquals(JobMonitor.Connection.MONITORING, events.getLast().connection());
                assertEquals(0, events.getLast().snapshot().completedUnits());
                updates.accept(done(1));
            }
        };
        monitor(service, events).run();
        assertEquals(2, service.watches);
        assertEquals(JobMonitor.Connection.FINISHED, events.getLast().connection());
    }
    @Test void staleAttemptInNewRevisionIsRejectedWithoutCountingIt() {
        List<JobMonitor.Update> events = new ArrayList<>();
        Fixture service = new Fixture() {
            @Override public void watchJob(String id, Consumer<JobSnapshot> updates) {
                updates.accept(running(1, 2, 3, 0));
                updates.accept(running(2, 1, 1, 4));
            }
        };
        monitor(service, events).run();
        assertEquals(JobMonitor.Connection.UNAVAILABLE, events.getLast().connection());
        assertEquals(0, events.getLast().snapshot().completedUnits());
        assertTrue(events.getLast().detail().contains("attempt"));
    }
    @Test void uncertainSubmissionLooksUpSameKeyInsteadOfResubmitting() {
        List<JobMonitor.Update> events = new ArrayList<>();
        Fixture service = new Fixture() {
            @Override public JobSnapshot submit(String key, Job input) throws Unavailable {
                submits++; keys.add(key); throw new Unavailable("reply lost after admission");
            }
        };
        monitor(service, events).run();
        assertEquals(1, service.submits);
        assertEquals(1, service.lookups);
        assertEquals(List.of("original-key", "original-key"), service.keys);
        assertTrue(events.stream().anyMatch(e -> e.connection() == JobMonitor.Connection.RECONNECTING
                && e.jobId() == null && e.snapshot() == null));
        assertEquals(JobMonitor.Connection.FINISHED, events.getLast().connection());
    }
    @Test void majorityUnavailableOrUnknownAdmissionNeverProducesFalseSuccess() {
        List<JobMonitor.Update> events = new ArrayList<>();
        Fixture service = new Fixture() {
            @Override public JobSnapshot submit(String key, Job input) throws Unavailable {
                submits++; throw new Unavailable("fixture: majority unavailable");
            }
            @Override public JobSnapshot lookupSubmission(String key) throws NotFound {
                lookups++; throw new NotFound("fixture: unresolved submission");
            }
        };
        monitor(service, events).run();
        assertEquals(1, service.submits);
        assertEquals(2, service.lookups);
        assertEquals(JobMonitor.Connection.UNAVAILABLE, events.getLast().connection());
        assertTrue(events.stream().allMatch(e -> e.snapshot() == null && e.jobId() == null));
    }
    @Test void explicitResumeWithKeyOnlyReconcilesWithoutAnotherSubmission() {
        List<JobMonitor.Update> events = new ArrayList<>();
        Fixture service = new Fixture();
        new JobMonitor(service, null, "original-key", null, events::add, FAST).run();
        assertEquals(0, service.submits);
        assertEquals(1, service.lookups);
        assertEquals(JobMonitor.Connection.FINISHED, events.getLast().connection());
    }
    @Test void missingPreviouslyConfirmedJobDoesNotBecomeNewSubmission() {
        List<JobMonitor.Update> events = new ArrayList<>();
        Fixture service = new Fixture() {
            @Override public JobSnapshot getJob(String id) throws NotFound {
                gets++; throw new NotFound("recovery record missing");
            }
        };
        new JobMonitor(service, null, "key", "stable-job", events::add, FAST).run();
        assertEquals(0, service.submits);
        assertEquals(3, service.gets);
        assertEquals("stable-job", events.getLast().jobId());
        assertEquals(JobMonitor.Connection.UNAVAILABLE, events.getLast().connection());
    }
    @Test void normalStreamEofTriggersReconnectAndPermanentFailureStaysPermanent() {
        List<JobMonitor.Update> events = new ArrayList<>();
        Fixture service = new Fixture() {
            @Override public void watchJob(String id, Consumer<JobSnapshot> updates) {
                if (++watches == 1) return;
                updates.accept(new JobSnapshot("stable-job", 1, JobSnapshot.State.FAILED,
                        10, 0, running(0, 1, 1, 0).tasks(), null, "calculation failed"));
            }
        };
        monitor(service, events).run();
        assertEquals(2, service.watches);
        assertEquals(JobSnapshot.State.FAILED, events.getLast().snapshot().state());
        assertEquals(0, events.getLast().snapshot().percent());
    }
    @Test void watchIsCancelledByInterruptWithoutClosingSharedClient() throws Exception {
        List<JobMonitor.Update> events = new CopyOnWriteArrayList<>();
        CountDownLatch watching = new CountDownLatch(1);
        Fixture service = new Fixture() {
            @Override public void watchJob(String id, Consumer<JobSnapshot> updates) throws InterruptedException {
                watching.countDown(); new CountDownLatch(1).await();
            }
        };
        JobMonitor monitor = monitor(service, events);
        Thread thread = Thread.ofVirtual().start(monitor);
        try {
            assertTrue(watching.await(2, TimeUnit.SECONDS));
            monitor.close(); thread.join(2000);
            assertFalse(thread.isAlive());
            assertEquals(JobMonitor.Connection.CANCELLED, events.getLast().connection());
            assertEquals("stable-job", events.getLast().jobId());
        } finally { monitor.close(); }
    }
    @Test void changedIdAndConflictingRevisionCannotOverwriteKnownJob() {
        for (boolean changedId : List.of(false, true)) {
            List<JobMonitor.Update> events = new ArrayList<>();
            Fixture service = new Fixture() {
                @Override public void watchJob(String id, Consumer<JobSnapshot> updates) {
                    var old = running(0, 1, 1, 0);
                    updates.accept(new JobSnapshot(changedId ? "other-job" : old.jobId(), 0,
                            old.state(), 10, 4, running(0, 1, 1, 4).tasks(), null, null));
                }
            };
            monitor(service, events).run();
            assertEquals(JobMonitor.Connection.UNAVAILABLE, events.getLast().connection());
            assertEquals("stable-job", events.getLast().jobId());
            assertEquals(0, events.getLast().snapshot().completedUnits());
        }
    }
}
