package edu.usp.cs324.client.progress;

import edu.usp.cs324.api.Job;
import edu.usp.cs324.client.JobInput;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;

/** Separate development GUI; the legacy A1 GUI and headless client are preserved. */
public final class ProgressGui extends JFrame {
    private final JobClient client;
    private final ExecutorService monitors = Executors.newVirtualThreadPerTaskExecutor();
    private final List<JobMonitor> active = new ArrayList<>();
    private final JPanel jobs = new JPanel();
    private final JComboBox<Job.Type> type = new JComboBox<>(Job.Type.values());
    private final JTextArea input = new JTextArea("1,2,3,4,5", 4, 50);
    private final JTextField existingId = new JTextField(30);
    private final JLabel inputStatus = new JLabel("Headerless integer CSV; PRIMESUM endpoints are inclusive.");
    private boolean closed;
    private int rows;

    private final class Row {
        final String key;
        final JobProgressPanel panel;
        JobMonitor.Update last;
        JobMonitor monitor;
        Row(String description, String key) {
            this.key = key;
            panel = new JobProgressPanel(description, () -> start(null, last == null ? null : last.jobId()));
        }
        void start(Job job, String id) {
            if (monitor != null) { monitor.close(); active.remove(monitor); }
            monitor = new JobMonitor(client, job, key, id, update -> SwingUtilities.invokeLater(() -> {
                if (!closed) { last = update; panel.update(update); }
            }), JobMonitor.DEFAULT_BACKOFF);
            active.add(monitor);
            panel.resumeButton().setEnabled(false);
            monitors.submit(monitor);
        }
    }
    public ProgressGui(JobClient client, String serviceDescription) {
        super("DistriLab progress — " + serviceDescription);
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("GUI must be constructed on EDT");
        this.client = Objects.requireNonNull(client);
        JPanel editor = new JPanel(new BorderLayout(8, 8));
        JLabel banner = new JLabel(serviceDescription);
        banner.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        editor.add(banner, BorderLayout.NORTH);
        editor.add(new JScrollPane(input), BorderLayout.CENTER);
        JPanel actions = new JPanel();
        JButton submit = new JButton("Submit entered data");
        JButton csv = new JButton("Submit CSV file...");
        actions.add(type); actions.add(submit); actions.add(csv);
        submit.addActionListener(event -> submit(null));
        csv.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                submit(chooser.getSelectedFile().toPath());
        });
        JPanel resume = new JPanel();
        resume.add(new JLabel("Existing job ID")); resume.add(existingId);
        JButton reconnect = new JButton("Monitor existing job"); resume.add(reconnect);
        reconnect.addActionListener(event -> {
            String id = existingId.getText().strip();
            if (id.isBlank()) { inputStatus.setText("Enter an existing job ID"); return; }
            Row row = addRow("Existing job " + id, UUID.randomUUID().toString());
            row.start(null, id);
        });
        if (client instanceof SimulatedJobClient simulation) {
            JButton disconnect = new JButton("Simulate stream disconnect");
            disconnect.addActionListener(event -> simulation.disconnectStreams());
            resume.add(disconnect);
        }
        JPanel controls = new JPanel(new GridLayout(0, 1));
        controls.add(actions); controls.add(resume); controls.add(inputStatus);
        editor.add(controls, BorderLayout.SOUTH);
        jobs.setLayout(new BoxLayout(jobs, BoxLayout.Y_AXIS));
        add(editor, BorderLayout.NORTH); add(new JScrollPane(jobs), BorderLayout.CENTER);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { shutdown(); }
        });
        setSize(1100, 700); setLocationByPlatform(true);
    }
    private Row addRow(String description, String key) {
        Row row = new Row(description, key);
        jobs.add(row.panel); jobs.revalidate(); jobs.repaint();
        return row;
    }
    private void submit(Path file) {
        Job.Type selected = (Job.Type) type.getSelectedItem();
        String data = input.getText();
        String key = UUID.randomUUID().toString();
        inputStatus.setText("Reading and validating input...");
        new SwingWorker<Job, Void>() {
            @Override protected Job doInBackground() throws Exception {
                return file == null ? JobInput.parse(selected, data) : JobInput.read(selected, file);
            }
            @Override protected void done() {
                if (closed) return;
                try {
                    Job job = get();
                    addRow("Job " + (++rows) + " — " + selected, key).start(job, null);
                    inputStatus.setText("Input validated; waiting for service admission");
                } catch (Exception failure) {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    inputStatus.setText("Input rejected; nothing submitted: " + cause.getMessage());
                }
            }
        }.execute();
    }
    private void shutdown() {
        if (closed) return;
        closed = true;
        for (JobMonitor monitor : active) monitor.close();
        monitors.shutdownNow();
        Thread.ofVirtual().name("job-client-shutdown").start(client::close);
    }
    public static void main(String[] args) {
        if (args.length != 1 || !args[0].equals("--simulate"))
            throw new IllegalArgumentException("ProgressGui --simulate (development only; no gRPC adapter yet)");
        SwingUtilities.invokeLater(() -> new ProgressGui(new SimulatedJobClient(),
                "DEVELOPMENT SIMULATION — local calculations only; no gRPC, quorum or real recovery")
                .setVisible(true));
    }
}
