package edu.usp.cs324.integration;

import edu.usp.cs324.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.net.ServerSocket;
import java.nio.file.*;
import java.rmi.registry.LocateRegistry;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Area 1 only: real JVMs prove registration/status transport, not elections/jobs. */
class MembershipIT {
    private final List<Process> processes = new ArrayList<>();
    private final Set<Integer> allocatedPorts = new HashSet<>();
    private final Path logs = Path.of("target", "membership-integration").toAbsolutePath();

    private int port() throws Exception {
        for (;;) {
            try (ServerSocket socket = new ServerSocket(0)) {
                int port = socket.getLocalPort();
                if (allocatedPorts.add(port)) return port;
            }
        }
    }

    private Process start(String log, String main, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dsun.rmi.transport.tcp.responseTimeout=10000", "-cp",
                Path.of("target", "classes").toAbsolutePath().toString(), main));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(logs.resolve(log).toFile()).start();
        processes.add(process);
        return process;
    }

    private void ready(Process process, String log, String marker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            String output = Files.readString(logs.resolve(log));
            if (output.contains(marker)) return;
            assertTrue(process.isAlive(), output);
            Thread.sleep(100);
        }
        fail("Readiness timeout: " + log);
    }

    @Test @Timeout(90)
    void bootstrapAndFourWorkerProcessesExposeMembershipWithoutImplementedFeatures() throws Exception {
        Files.createDirectories(logs);
        try {
            int bootstrapPort = port();
            Process bootstrapProcess = start("bootstrap.log", "edu.usp.cs324.network.BootstrapNode",
                    "127.0.0.1", "" + bootstrapPort, "" + port());
            ready(bootstrapProcess, "bootstrap.log", "BOOTSTRAP READY");
            for (int id = 1; id <= 4; id++) {
                Process worker = start("worker" + id + ".log", "edu.usp.cs324.network.WorkerNode",
                        "" + id, "127.0.0.1", "" + port(), "" + port(), "127.0.0.1", "" + bootstrapPort);
                ready(worker, "worker" + id + ".log", "WORKER READY");
            }
            BootstrapRemote bootstrap = (BootstrapRemote) LocateRegistry.getRegistry("127.0.0.1", bootstrapPort).lookup("bootstrap");
            List<Peer> members = bootstrap.active();
            assertEquals(4, members.size());
            Set<Peer> reached = new HashSet<>();
            Deque<Peer> pending = new ArrayDeque<>(List.of(members.getFirst()));
            int edges = 0;
            while (!pending.isEmpty()) {
                Peer peer = pending.remove();
                if (!reached.add(peer)) continue;
                Status status = peer.connect().status();
                assertNull(status.term());
                assertEquals(0, status.jac());
                assertEquals(0, status.assignedJobs());
                edges += status.neighbours().size();
                for (Peer neighbour : status.neighbours()) {
                    assertTrue(neighbour.connect().status().neighbours().contains(peer));
                    pending.add(neighbour);
                }
            }
            assertEquals(new HashSet<>(members), reached);
            assertEquals(6, edges);
            assertThrows(java.rmi.RemoteException.class, () -> members.getFirst().connect().elect());
            Process status = start("status.log", "edu.usp.cs324.network.ClusterStatus", "127.0.0.1", "" + bootstrapPort);
            assertTrue(status.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, status.exitValue());
            assertEquals(4, Files.readAllLines(logs.resolve("status.log")).size());
            Files.writeString(logs.resolve("summary.txt"), "PASS: bootstrap + 4 workers + status process; connected reciprocal membership; no election implementation.\n");
        } finally {
            for (Process process : processes.reversed()) {
                process.destroy();
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(3, TimeUnit.SECONDS);
                }
            }
        }
    }
}
