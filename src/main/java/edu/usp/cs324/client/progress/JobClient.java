package edu.usp.cs324.client.progress;

import edu.usp.cs324.api.Job;
import java.util.function.Consumer;

/** Transport boundary for UI development; does not approve or implement the proposed RPCs.
 * Implementations must bound discovery/unary calls, make watch interruption cancellable,
 * and support concurrent independent monitors. close must release channels/streams promptly.
 */
public interface JobClient extends AutoCloseable {
    void locateCoordinator() throws Unavailable, InterruptedException;
    JobSnapshot submit(String submissionKey, Job input) throws Unavailable, InterruptedException;
    JobSnapshot lookupSubmission(String submissionKey) throws Unavailable, NotFound, InterruptedException;
    JobSnapshot getJob(String jobId) throws Unavailable, NotFound, InterruptedException;
    // Begins with the current full snapshot. Returning without terminal state is a disconnect.
    void watchJob(String jobId, Consumer<JobSnapshot> updates)
            throws Unavailable, NotFound, InterruptedException;
    @Override void close();

    class Unavailable extends Exception {
        public Unavailable(String message) { super(message); }
    }
    class NotFound extends Exception {
        public NotFound(String message) { super(message); }
    }
}
