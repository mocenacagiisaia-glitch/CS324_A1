package edu.usp.cs324.client.progress;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JobSnapshotTest {
    @Test void weightedWorkProgressAndWorkerBreakdownUseIdenticalCredits() {
        var snapshot = JobMonitorTest.running(1, 2, 3, 4);
        assertEquals(40, snapshot.percent());
        assertEquals(snapshot.completedUnits(), snapshot.workerContributions().values()
                .stream().mapToLong(Long::longValue).sum());
    }
    @Test void emptyJobRequiresCommittedCompletionBeforeShowing100Percent() {
        var tasks = List.of(new JobSnapshot.Task("empty", 1, 1, 0, 0));
        assertEquals(0, new JobSnapshot("id", 0, JobSnapshot.State.ACCEPTED,
                0, 0, tasks, null, null).percent());
        assertEquals(100, new JobSnapshot("id", 1, JobSnapshot.State.COMPLETED,
                0, 0, tasks, BigInteger.ZERO, null).percent());
    }
    @Test void duplicateTasksAndInconsistentCountsAreRejected() {
        var task = new JobSnapshot.Task("task", 1, 1, 10, 5);
        assertThrows(IllegalArgumentException.class, () -> new JobSnapshot("id", 0,
                JobSnapshot.State.RUNNING, 20, 10, List.of(task, task), null, null));
        assertThrows(IllegalArgumentException.class, () -> new JobSnapshot("id", 0,
                JobSnapshot.State.RUNNING, 10, 6, List.of(task), null, null));
        assertThrows(IllegalArgumentException.class, () -> new JobSnapshot("id", 0,
                JobSnapshot.State.COMPLETED, 10, 5, List.of(task), BigInteger.ONE, null));
        assertThrows(IllegalArgumentException.class, () -> new JobSnapshot.Task("task", 1, null, 10, 1));
    }
    @Test void progressCalculationDoesNotOverflowLong() {
        var task = new JobSnapshot.Task("large", 1, 1, Long.MAX_VALUE, Long.MAX_VALUE - 1);
        assertEquals(99, new JobSnapshot("id", 1, JobSnapshot.State.RUNNING,
                task.totalUnits(), task.completedUnits(), List.of(task), null, null).percent());
    }
}
