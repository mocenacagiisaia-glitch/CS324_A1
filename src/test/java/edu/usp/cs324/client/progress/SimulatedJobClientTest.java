package edu.usp.cs324.client.progress;

import edu.usp.cs324.api.Job;
import edu.usp.cs324.client.JobInput;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

/** Single-process simulator tests only; no distributed failure-recovery evidence. */
class SimulatedJobClientTest {
    @Test @Timeout(10) void simulationUsesRealLocalCalculationsForAllTypesAndStableKeys() throws Exception {
        try (var client = new SimulatedJobClient()) {
            List<Job> jobs = List.of(JobInput.parse(Job.Type.MAX, "-9,42,7"),
                    JobInput.parse(Job.Type.PRIMESUM, "1,1000"),
                    JobInput.parse(Job.Type.PRIMECOUNT, "2,2,3,4,5"),
                    JobInput.parse(Job.Type.PRIMECOUNT, ""));
            List<BigInteger> expected = List.of(BigInteger.valueOf(42), BigInteger.valueOf(76127),
                    BigInteger.valueOf(4), BigInteger.ZERO);
            for (int i = 0; i < jobs.size(); i++) {
                String key = "simulation-key-" + i;
                var accepted = client.submit(key, jobs.get(i));
                List<JobSnapshot> seen = new ArrayList<>();
                client.watchJob(accepted.jobId(), seen::add);
                var done = seen.getLast();
                assertEquals(expected.get(i), done.result());
                assertEquals(100, done.percent());
                assertEquals(done.completedUnits(), done.workerContributions().values().stream()
                        .mapToLong(Long::longValue).sum());
                assertEquals(done.jobId(), client.submit(key, jobs.get(i)).jobId());
                assertEquals(done.jobId(), client.lookupSubmission(key).jobId());
            }
            assertThrows(IllegalArgumentException.class, () -> client.submit("simulation-key-0", jobs.get(1)));
        }
    }
    @Test void closedSimulatorRejectsNetworkLikeOperations() throws Exception {
        var client = new SimulatedJobClient();
        client.close();
        assertThrows(JobClient.Unavailable.class, client::locateCoordinator);
        assertThrows(JobClient.Unavailable.class, () -> client.getJob("missing"));
    }
    @Test @Timeout(10) void concurrentSimulationJobsRemainIndependent() throws Exception {
        try (var client = new SimulatedJobClient(); var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<BigInteger>> jobs = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                int value = i;
                jobs.add(pool.submit(() -> {
                    var receipt = client.submit("concurrent-" + value,
                            new Job(Job.Type.MAX, List.of(value, -1), 0, 0));
                    List<JobSnapshot> seen = new ArrayList<>();
                    client.watchJob(receipt.jobId(), seen::add);
                    return seen.getLast().result();
                }));
            }
            for (int i = 0; i < jobs.size(); i++) assertEquals(BigInteger.valueOf(i), jobs.get(i).get());
        }
    }
}
