package edu.usp.cs324.client.progress;

import java.math.BigInteger;
import java.util.*;

/** Local UI model, not an approved wire protocol. Full authoritative snapshots only. */
public record JobSnapshot(String jobId, long revision, State state, long totalUnits,
                          long completedUnits, List<Task> tasks, BigInteger result, String failure) {
    public enum State { ACCEPTED, RUNNING, COMPLETED, FAILED, TEMPORARILY_UNAVAILABLE }
    public record Task(String taskId, long attempt, Integer workerId, long totalUnits,
                       long completedUnits) {
        public Task {
            if (taskId == null || taskId.isBlank() || attempt < 1 || totalUnits < 0
                    || completedUnits < 0 || completedUnits > totalUnits)
                throw new IllegalArgumentException("Invalid task snapshot");
            if (completedUnits > 0 && workerId == null)
                throw new IllegalArgumentException("Completed work needs a worker");
        }
    }
    public JobSnapshot {
        if (jobId == null || jobId.isBlank() || revision < 0 || totalUnits < 0
                || completedUnits < 0 || completedUnits > totalUnits)
            throw new IllegalArgumentException("Invalid job snapshot");
        Objects.requireNonNull(state);
        tasks = List.copyOf(tasks);
        Set<String> ids = new HashSet<>();
        long total = 0, completed = 0;
        for (Task task : tasks) {
            if (!ids.add(task.taskId())) throw new IllegalArgumentException("Duplicate task ID");
            total = Math.addExact(total, task.totalUnits());
            completed = Math.addExact(completed, task.completedUnits());
        }
        if (total != totalUnits || completed != completedUnits)
            throw new IllegalArgumentException("Job and task work counts disagree");
        if (state == State.COMPLETED && (completedUnits != totalUnits || result == null))
            throw new IllegalArgumentException("Completed job needs all work and a result");
        if (state != State.COMPLETED && result != null)
            throw new IllegalArgumentException("Non-completed job cannot claim a final result");
        if (state == State.FAILED && (failure == null || failure.isBlank()))
            throw new IllegalArgumentException("Failed job needs a reason");
    }
    public boolean terminal() { return state == State.COMPLETED || state == State.FAILED; }
    public int percent() {
        if (totalUnits == 0) return state == State.COMPLETED ? 100 : 0;
        return BigInteger.valueOf(completedUnits).multiply(BigInteger.valueOf(100))
                .divide(BigInteger.valueOf(totalUnits)).intValueExact();
    }
    public Map<Integer, Long> workerContributions() {
        Map<Integer, Long> units = new TreeMap<>();
        for (Task task : tasks)
            if (task.workerId() != null)
                units.merge(task.workerId(), task.completedUnits(), Math::addExact);
        return Collections.unmodifiableMap(units);
    }
}
