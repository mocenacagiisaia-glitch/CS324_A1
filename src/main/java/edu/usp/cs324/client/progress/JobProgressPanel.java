package edu.usp.cs324.client.progress;

import java.awt.*;
import java.awt.event.*;
import java.util.Map;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** EDT-only view. Transport callbacks must marshal updates onto the EDT. */
public final class JobProgressPanel extends JPanel {
    private final JLabel identity = new JLabel("Admission unconfirmed");
    private final JLabel connection = new JLabel("Waiting");
    private final JLabel state = new JLabel("No confirmed job state");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JButton breakdown = new JButton("Worker breakdown");
    private final JButton resume = new JButton("Resume monitoring");
    private final JTextField result = new JTextField();
    private final DefaultTableModel workers = new DefaultTableModel(
            new String[]{"Worker", "Credited work units", "Share of job (%)"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private JobSnapshot snapshot;
    public JobProgressPanel(String description, Runnable resumeAction) {
        requireEdt();
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createTitledBorder(description));
        JPanel labels = new JPanel(new GridLayout(0, 1));
        labels.add(identity); labels.add(connection); labels.add(state);
        progress.setStringPainted(true);
        progress.getAccessibleContext().setAccessibleName("Confirmed job completion percentage");
        progress.setToolTipText("Click for worker contributions; or use Worker breakdown");
        progress.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { showBreakdown(); }
        });
        JPanel buttons = new JPanel(); buttons.add(breakdown); buttons.add(resume);
        breakdown.addActionListener(event -> showBreakdown());
        resume.addActionListener(event -> resumeAction.run());
        breakdown.setEnabled(false); resume.setEnabled(false);
        result.setEditable(false);
        result.getAccessibleContext().setAccessibleName("Job result or permanent failure detail");
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(result, BorderLayout.CENTER); bottom.add(buttons, BorderLayout.SOUTH);
        add(labels, BorderLayout.NORTH); add(progress, BorderLayout.CENTER); add(bottom, BorderLayout.SOUTH);
    }
    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Swing update outside EDT");
    }
    public void update(JobMonitor.Update update) {
        requireEdt();
        identity.setText("Job: " + (update.jobId() == null ? "unconfirmed" : update.jobId())
                + " | Submission key: " + update.submissionKey());
        connection.setText(update.connection() + ": " + update.detail());
        resume.setEnabled(update.connection() == JobMonitor.Connection.UNAVAILABLE
                || update.connection() == JobMonitor.Connection.CANCELLED);
        if (update.snapshot() == null) return;
        snapshot = update.snapshot();
        state.setText("Last confirmed job state: " + snapshot.state());
        progress.setValue(snapshot.percent());
        progress.setString(snapshot.percent() + "% (" + snapshot.completedUnits() + "/"
                + snapshot.totalUnits() + " work units)");
        breakdown.setEnabled(true);
        result.setText(snapshot.result() != null ? snapshot.result().toString()
                : snapshot.state() == JobSnapshot.State.FAILED ? snapshot.failure() : "");
        workers.setRowCount(0);
        for (Map.Entry<Integer, Long> worker : snapshot.workerContributions().entrySet()) {
            double percent = snapshot.totalUnits() == 0 ? 0
                    : 100.0 * worker.getValue() / snapshot.totalUnits();
            workers.addRow(new Object[]{worker.getKey(), worker.getValue(), String.format("%.1f", percent)});
        }
    }
    private void showBreakdown() {
        if (snapshot == null) return;
        JTable table = new JTable(workers);
        table.getAccessibleContext().setAccessibleName("Worker contributions to confirmed work");
        JOptionPane.showMessageDialog(this, new JScrollPane(table),
                "Worker breakdown — " + snapshot.jobId(), JOptionPane.PLAIN_MESSAGE);
    }
    // Package-visible component access allows headless EDT tests without a JFrame.
    JProgressBar progressBar() { return progress; }
    JButton resumeButton() { return resume; }
    JButton breakdownButton() { return breakdown; }
    DefaultTableModel workerTable() { return workers; }
    String connectionText() { return connection.getText(); }
    String stateText() { return state.getText(); }
}
