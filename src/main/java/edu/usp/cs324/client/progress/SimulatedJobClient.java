package edu.usp.cs324.client.progress;

import edu.usp.cs324.api.Job;
import edu.usp.cs324.jobs.IntegerJobEngine;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** In-memory, single-process DEVELOPMENT SIMULATION. No gRPC, quorum or failover. */
public final class SimulatedJobClient implements JobClient {
    private static final class Entry {
        final Job input;
        JobSnapshot snapshot;
        Entry(Job input, JobSnapshot snapshot) { this.input = input; this.snapshot = snapshot; }
    }
    private final Map<String, Entry> keys = new HashMap<>();
    private final Map<String, Entry> jobs = new HashMap<>();
    private final ExecutorService calculations = Executors.newVirtualThreadPerTaskExecutor();
    private final IntegerJobEngine engine = new IntegerJobEngine();
    private long streamGeneration;
    private boolean loseNextSubmissionReply;
    private boolean closed;

    private void available() throws Unavailable {
        if (closed) throw new Unavailable("Simulation is closed");
    }
    @Override public synchronized void locateCoordinator() throws Unavailable { available(); }
    @Override public synchronized JobSnapshot submit(String key, Job input) throws Unavailable {
        available();
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Submission key required");
        Objects.requireNonNull(input);
        Entry prior = keys.get(key);
        if (prior != null) {
            if (!prior.input.equals(input)) throw new IllegalArgumentException("Submission key input changed");
            return prior.snapshot;
        }
        String id = "sim-" + UUID.randomUUID();
        List<Job> parts = engine.split(input, 4);
        List<JobSnapshot.Task> tasks = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            Job part = parts.get(i);
            long units = part.type() == Job.Type.PRIMESUM
                    ? (long) part.end() - part.start() + 1 : part.numbers().size();
            tasks.add(new JobSnapshot.Task(id + "-task-" + i, 1, i + 1, units, 0));
        }
        long total = tasks.stream().mapToLong(JobSnapshot.Task::totalUnits).sum();
        Entry entry = new Entry(input, new JobSnapshot(id, 0, JobSnapshot.State.ACCEPTED,
                total, 0, tasks, null, null));
        keys.put(key, entry); jobs.put(id, entry);
        calculations.submit(() -> calculate(entry, parts));
        if (loseNextSubmissionReply) {
            loseNextSubmissionReply = false;
            throw new Unavailable("Simulated submission reply lost after local admission");
        }
        return entry.snapshot;
    }
    private void calculate(Entry entry, List<Job> parts) {
        List<BigInteger> results = new ArrayList<>();
        try {
            for (int i = 0; i < parts.size(); i++) {
                if (Thread.currentThread().isInterrupted()) return;
                BigInteger result = engine.compute(parts.get(i));
                results.add(result);
                synchronized (this) {
                    if (closed) return;
                    JobSnapshot old = entry.snapshot;
                    List<JobSnapshot.Task> tasks = new ArrayList<>(old.tasks());
                    JobSnapshot.Task task = tasks.get(i);
                    tasks.set(i, new JobSnapshot.Task(task.taskId(), task.attempt(), task.workerId(),
                            task.totalUnits(), task.totalUnits()));
                    boolean done = i == parts.size() - 1;
                    entry.snapshot = new JobSnapshot(old.jobId(), old.revision() + 1,
                            done ? JobSnapshot.State.COMPLETED : JobSnapshot.State.RUNNING,
                            old.totalUnits(), old.completedUnits() + task.totalUnits(), tasks,
                            done ? engine.aggregate(entry.input.type(), results) : null, null);
                    notifyAll();
                }
            }
        } catch (RuntimeException failure) {
            synchronized (this) {
                if (closed) return;
                JobSnapshot old = entry.snapshot;
                entry.snapshot = new JobSnapshot(old.jobId(), old.revision() + 1,
                        JobSnapshot.State.FAILED, old.totalUnits(), old.completedUnits(), old.tasks(),
                        null, failure.toString());
                notifyAll();
            }
        }
    }
    @Override public synchronized JobSnapshot lookupSubmission(String key) throws Unavailable, NotFound {
        available();
        Entry entry = keys.get(key);
        if (entry == null) throw new NotFound("Simulation submission key not found");
        return entry.snapshot;
    }
    @Override public synchronized JobSnapshot getJob(String id) throws Unavailable, NotFound {
        available();
        Entry entry = jobs.get(id);
        if (entry == null) throw new NotFound("Simulation job not found (records last for this process only)");
        return entry.snapshot;
    }
    @Override public void watchJob(String id, Consumer<JobSnapshot> updates)
            throws Unavailable, NotFound, InterruptedException {
        long generation;
        synchronized (this) { available(); generation = streamGeneration; }
        long revision = -1;
        for (;;) {
            JobSnapshot next;
            synchronized (this) {
                while (true) {
                    available();
                    if (generation != streamGeneration)
                        throw new Unavailable("Simulated stream disconnect; no real coordinator failure");
                    next = getJob(id);
                    if (next.revision() != revision) break;
                    wait();
                }
            }
            updates.accept(next);
            if (next.terminal()) return;
            revision = next.revision();
        }
    }
    public synchronized void disconnectStreams() { streamGeneration++; notifyAll(); }
    public synchronized void loseNextSubmissionReply() { loseNextSubmissionReply = true; }
    @Override public synchronized void close() {
        closed = true;
        notifyAll();
        calculations.shutdownNow();
    }
}
