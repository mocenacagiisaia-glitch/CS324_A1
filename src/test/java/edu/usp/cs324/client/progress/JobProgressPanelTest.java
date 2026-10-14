package edu.usp.cs324.client.progress;

import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Headless Swing component tests. Does not claim a visual/manual GUI demonstration. */
class JobProgressPanelTest {
    @Test void reconnectPreservesProgressAndExposesWorkerDetailsAndResume() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicBoolean resumed = new AtomicBoolean();
            var panel = new JobProgressPanel("fixture job", () -> resumed.set(true));
            var snapshot = JobMonitorTest.running(1, 2, 3, 4);
            panel.update(new JobMonitor.Update(JobMonitor.Connection.MONITORING, "fixture",
                    "key", snapshot.jobId(), snapshot));
            assertEquals(40, panel.progressBar().getValue());
            assertEquals(2, panel.workerTable().getRowCount());
            assertEquals(3, panel.workerTable().getValueAt(1, 0));
            assertEquals(4L, panel.workerTable().getValueAt(1, 1));
            assertTrue(panel.breakdownButton().isEnabled());
            assertEquals(1, panel.breakdownButton().getActionListeners().length);
            assertTrue(panel.progressBar().getMouseListeners().length > 0);
            assertEquals("Confirmed job completion percentage",
                    panel.progressBar().getAccessibleContext().getAccessibleName());
            panel.update(new JobMonitor.Update(JobMonitor.Connection.RECONNECTING, "stream interrupted",
                    "key", snapshot.jobId(), snapshot));
            assertEquals(40, panel.progressBar().getValue());
            assertTrue(panel.connectionText().contains("RECONNECTING"));
            assertTrue(panel.stateText().contains("RUNNING"));
            assertFalse(panel.resumeButton().isEnabled());
            panel.update(new JobMonitor.Update(JobMonitor.Connection.UNAVAILABLE, "retry limit",
                    "key", snapshot.jobId(), snapshot));
            assertEquals(40, panel.progressBar().getValue());
            panel.resumeButton().doClick();
            assertTrue(resumed.get());
        });
    }
    @Test void unconfirmedAdmissionHasNoPercentageOrWorkerSuccess() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new JobProgressPanel("fixture", () -> { });
            panel.update(new JobMonitor.Update(JobMonitor.Connection.UNAVAILABLE, "no majority",
                    "key", null, null));
            assertEquals(0, panel.progressBar().getValue());
            assertEquals(0, panel.workerTable().getRowCount());
            assertFalse(panel.breakdownButton().isEnabled());
            assertTrue(panel.stateText().contains("No confirmed"));
        });
    }
    @Test void componentRejectsOffEdtMutations() throws Exception {
        AtomicReference<JobProgressPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new JobProgressPanel("fixture", () -> { })));
        assertThrows(IllegalStateException.class, () -> panel.get().update(
                new JobMonitor.Update(JobMonitor.Connection.SUBMITTING, "fixture", "key", null, null)));
    }
}
