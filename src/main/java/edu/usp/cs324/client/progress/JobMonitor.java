package edu.usp.cs324.client.progress;

import edu.usp.cs324.api.Job;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/** One background monitor. Retries monitoring/reconciliation, never submission. */
public final class JobMonitor implements Runnable, AutoCloseable {
    public enum Connection { SUBMITTING, MONITORING, RECONNECTING, UNAVAILABLE, FINISHED, CANCELLED }
    public record Update(Connection connection, String detail, String submissionKey,
                         String jobId, JobSnapshot snapshot) { }
    public static final List<Duration> DEFAULT_BACKOFF = List.of(Duration.ofMillis(250),
            Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(2),
            Duration.ofSeconds(4), Duration.ofSeconds(4));
    private final JobClient client;
    private final Job input;
    private final String key;
    private final Consumer<Update> listener;
    private final List<Duration> backoff;
    private String jobId;
    private JobSnapshot snapshot;
    private Connection connection;
    private volatile boolean cancelled;
    private Thread runner;
    private boolean started;

    public JobMonitor(JobClient client, Job input, String submissionKey, String existingJobId,
                      Consumer<Update> listener, List<Duration> backoff) {
        this.client = Objects.requireNonNull(client);
        this.input = input;
        this.key = Objects.requireNonNull(submissionKey);
        if (submissionKey.isBlank()
                || (existingJobId != null && existingJobId.isBlank()))
            throw new IllegalArgumentException("Input or an existing job ID is required");
        this.jobId = existingJobId;
        this.listener = Objects.requireNonNull(listener);
        this.backoff = List.copyOf(backoff);
        if (this.backoff.stream().anyMatch(Duration::isNegative))
            throw new IllegalArgumentException("Negative backoff");
    }
    private void emit(Connection connection, String detail) {
        this.connection = connection;
        listener.accept(new Update(connection, detail, key, jobId, snapshot));
    }
    private void accept(JobSnapshot next) {
        Objects.requireNonNull(next);
        if (jobId != null && !jobId.equals(next.jobId()))
            throw new IllegalArgumentException("Server changed the stable job ID");
        if (snapshot != null) {
            if (next.revision() < snapshot.revision()) return;
            if (next.revision() == snapshot.revision()) {
                if (!next.equals(snapshot)) throw new IllegalArgumentException("Conflicting snapshot revision");
                return;
            }
            if (snapshot.terminal()) throw new IllegalArgumentException("Terminal job changed");
            Map<String, JobSnapshot.Task> previous = new HashMap<>();
            for (var task : snapshot.tasks()) previous.put(task.taskId(), task);
            if (next.totalUnits() != snapshot.totalUnits() || next.tasks().size() != previous.size())
                throw new IllegalArgumentException("Logical job work changed");
            for (var task : next.tasks()) {
                var old = previous.get(task.taskId());
                if (old == null || old.totalUnits() != task.totalUnits() || task.attempt() < old.attempt())
                    throw new IllegalArgumentException("Invalid task identity/attempt transition");
            }
        }
        jobId = next.jobId();
        snapshot = next;
        emit(next.terminal() ? Connection.FINISHED : Connection.MONITORING,
                "Received authoritative snapshot");
    }
    @Override public void run() {
        synchronized (this) {
            if (started) throw new IllegalStateException("Monitor already started");
            started = true;
            runner = Thread.currentThread();
        }
        try {
            int retries = 0;
            boolean maySubmit = jobId == null && input != null;
            while (!cancelled) {
                try {
                    client.locateCoordinator();
                    if (maySubmit) {
                        emit(Connection.SUBMITTING, "Awaiting confirmed admission");
                        // An exception may mean admission happened: from now on reconcile this key only.
                        maySubmit = false;
                        accept(client.submit(key, input));
                    } else if (jobId == null) {
                        accept(client.lookupSubmission(key));
                    } else {
                        accept(client.getJob(jobId));
                    }
                    if (snapshot.terminal()) return;
                    if (connection != Connection.MONITORING)
                        emit(Connection.MONITORING, "Monitoring restored for the same job");
                    client.watchJob(jobId, this::accept);
                    if (snapshot.terminal()) return;
                    throw new JobClient.Unavailable("Monitoring stream ended before a terminal snapshot");
                } catch (JobClient.Unavailable | JobClient.NotFound failure) {
                    String detail = failure.getMessage() + (jobId == null
                            ? "; admission unconfirmed; retaining submission key" : "; retaining job " + jobId);
                    if (retries == backoff.size()) {
                        emit(Connection.UNAVAILABLE, detail + "; automatic retries exhausted");
                        return;
                    }
                    emit(Connection.RECONNECTING, detail);
                    Thread.sleep(backoff.get(retries++).toMillis());
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            emit(Connection.CANCELLED, "Monitoring stopped; accepted remote work is not cancelled");
        } catch (RuntimeException invalid) {
            emit(Connection.UNAVAILABLE, "Invalid snapshot/client error: " + invalid.getMessage());
        } finally {
            synchronized (this) { runner = null; }
        }
    }
    @Override public synchronized void close() {
        cancelled = true;
        if (runner != null) runner.interrupt();
    }
}
