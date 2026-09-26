package edu.usp.cs324.network;

import edu.usp.cs324.api.*;
import java.math.BigInteger;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;

/** Area 1 runtime only. Teammate 2 implements the explicitly marked protocol methods. */
public final class WorkerNode extends UnicastRemoteObject implements WorkerRemote, AutoCloseable {
    private final Peer self;
    private final BootstrapRemote bootstrap;
    private final Map<Integer, Peer> neighbours = new ConcurrentSkipListMap<>();
    private final ExecutorService execution = Executors.newVirtualThreadPerTaskExecutor();
    private final JobEngine engine;

    public WorkerNode(Peer self, BootstrapRemote bootstrap, int exportPort, JobEngine engine)
            throws RemoteException {
        super(exportPort);
        this.self = self;
        this.bootstrap = bootstrap;
        this.engine = engine;
    }

    @Override public synchronized Status status() {
        // No leader/JAC implementation yet. Teammate 2 must replace these initial values.
        return new Status(self, 0, 0, null, List.copyOf(neighbours.values()));
    }

    @Override public void addNeighbour(Peer peer) {
        if (peer.id() != self.id()) neighbours.put(peer.id(), peer);
    }

    private static RemoteException pendingCoordination() {
        return new RemoteException("Not implemented: teammate 2 election and coordination area");
    }

    @Override public List<Candidate> election(UUID id) throws RemoteException { throw pendingCoordination(); }
    @Override public void coordinator(Term term) throws RemoteException { throw pendingCoordination(); }
    @Override public Term elect() throws RemoteException { throw pendingCoordination(); }
    @Override public BigInteger submit(Job job) throws RemoteException { throw pendingCoordination(); }
    @Override public BigInteger assign(Job job, Term term, List<Peer> workers) throws RemoteException {
        throw pendingCoordination();
    }

    /** Executes one supplied chunk; splitting/algorithms belong to teammate 3's provider. */
    @Override public BigInteger compute(Job part) throws RemoteException {
        if (engine == null) throw new JobFailedException("Not implemented: teammate 3 JobEngine provider", null);
        Future<BigInteger> task;
        try { task = execution.submit(() -> engine.compute(part)); }
        catch (RuntimeException e) { throw new JobFailedException("Computation could not start", e); }
        InterruptedException interruption = null;
        try {
            for (;;) {
                try {
                    BigInteger result = task.get();
                    if (interruption != null) throw new JobFailedException("Interrupted; started computation has finished", interruption);
                    return result;
                } catch (InterruptedException e) {
                    interruption = e; // Drain the task before restoring the flag and reporting failure.
                } catch (ExecutionException e) {
                    throw new JobFailedException("Computation failed; task has finished", e.getCause());
                }
            }
        } finally {
            if (interruption != null) Thread.currentThread().interrupt();
        }
    }

    @Override public void close() throws java.rmi.NoSuchObjectException {
        execution.shutdown();
        UnicastRemoteObject.unexportObject(this, true);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 6) throw new IllegalArgumentException(
                "WorkerNode id host registryPort exportPort bootstrapHost bootstrapPort");
        System.setProperty("java.rmi.server.hostname", args[1]);
        BootstrapRemote bootstrap = (BootstrapRemote) LocateRegistry.getRegistry(args[4],
                Integer.parseInt(args[5])).lookup("bootstrap");
        Peer self = new Peer(Integer.parseInt(args[0]), args[1], Integer.parseInt(args[2]));
        JobEngine engine = ServiceLoader.load(JobEngine.class).findFirst().orElse(null);
        WorkerNode node = new WorkerNode(self, bootstrap, Integer.parseInt(args[3]), engine);
        LocateRegistry.createRegistry(self.registryPort()).rebind("worker", node);
        try { bootstrap.register(self); }
        catch (Exception e) { System.err.println("Startup failed: " + e); System.exit(1); }
        System.out.println("WORKER READY " + self.id() + " (membership only; elections pending)");
    }
}
