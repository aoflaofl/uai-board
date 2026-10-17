package spamalot.uaiboard.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import spamalot.uaiboard.play.CommandLine;
import spamalot.uaiboard.play.EngineConfig;
import spamalot.uaiboard.uai.UaiClient;

/** Lets the user add, edit, remove and test the engines the board can play against. */
public final class EnginesDialog extends JDialog {
    private final DefaultListModel<EngineConfig> model = new DefaultListModel<>();
    private final JList<EngineConfig> list = new JList<>(model);
    private final JTextField name = new JTextField(24);
    private final JTextField command = new JTextField(24);
    private final JTextArea options = new JTextArea(5, 24);
    private final JLabel testResult = new JLabel(" ");
    private int shown = -1;
    private List<EngineConfig> result;

    private EnginesDialog(Component parent, List<EngineConfig> engines) {
        super(SwingUtilities.getWindowAncestor(parent), "Engines", ModalityType.APPLICATION_MODAL);
        engines.forEach(model::addElement);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((l, value, index, selected, focus) -> {
            JLabel label = new JLabel(" " + value.name());
            label.setOpaque(true);
            label.setBackground(selected ? l.getSelectionBackground() : l.getBackground());
            label.setForeground(selected ? l.getSelectionForeground() : l.getForeground());
            return label;
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                commit();
                show(list.getSelectedIndex());
            }
        });

        JButton add = new JButton("Add");
        add.addActionListener(e -> {
            commit();
            model.addElement(new EngineConfig(uniqueName("New engine"), ""));
            list.setSelectedIndex(model.size() - 1);
            name.requestFocusInWindow();
            name.selectAll();
        });
        JButton remove = new JButton("Remove");
        remove.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i >= 0) {
                shown = -1;
                model.remove(i);
                if (!model.isEmpty()) {
                    list.setSelectedIndex(Math.min(i, model.size() - 1));
                } else {
                    show(-1);
                }
            }
        });
        JPanel left = new JPanel(new BorderLayout(0, 6));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(160, 260));
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftButtons.add(add);
        leftButtons.add(remove);
        left.add(scroll, BorderLayout.CENTER);
        left.add(leftButtons, BorderLayout.SOUTH);

        JButton browse = new JButton("Browse…");
        browse.addActionListener(e -> browse());
        JButton test = new JButton("Test");
        test.setToolTipText("Start the engine and read its name and options");
        test.addActionListener(e -> test(test));
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("Name"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(name, c);
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        form.add(new JLabel("Command"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(command, c);
        c.gridx = 2;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(browse, c);
        c.gridx = 0;
        c.gridy = 2;
        c.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel("<html>Options<br><small>Name=value,<br>one per line</small></html>"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        form.add(new JScrollPane(options), c);
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 1;
        c.weightx = 0;
        c.weighty = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.WEST;
        form.add(test, c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(testResult, c);

        JButton ok = new JButton("OK");
        ok.addActionListener(e -> accept());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ok);
        buttons.add(cancel);
        getRootPane().setDefaultButton(ok);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        content.add(left, BorderLayout.WEST);
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        pack();
        setLocationRelativeTo(parent);
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        } else {
            show(-1);
        }
    }

    /** Shows the dialog; returns the edited engine list, or null if the user cancelled. */
    public static List<EngineConfig> show(Component parent, List<EngineConfig> engines) {
        EnginesDialog d = new EnginesDialog(parent, engines);
        d.setVisible(true);
        return d.result;
    }

    private String uniqueName(String base) {
        Set<String> used = new HashSet<>();
        for (int i = 0; i < model.size(); i++) {
            used.add(model.get(i).name());
        }
        String candidate = base;
        for (int n = 2; used.contains(candidate); n++) {
            candidate = base + " " + n;
        }
        return candidate;
    }

    /** Puts the form's contents into the list entry that is being shown. */
    private void commit() {
        if (shown < 0 || shown >= model.size()) {
            return;
        }
        try {
            model.set(shown, new EngineConfig(name.getText().strip(), command.getText().strip(),
                    EngineConfig.parseOptions(options.getText())));
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Options", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void show(int index) {
        shown = index;
        boolean has = index >= 0 && index < model.size();
        name.setEnabled(has);
        command.setEnabled(has);
        options.setEnabled(has);
        testResult.setText(" ");
        if (has) {
            EngineConfig e = model.get(index);
            name.setText(e.name());
            command.setText(e.command());
            options.setText(e.optionsText());
        } else {
            name.setText("");
            command.setText("");
            options.setText("");
        }
    }

    private void browse() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose the engine program");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            String path = f.getAbsolutePath();
            // A .jar is run with java; anything else is run as it is.
            command.setText(path.endsWith(".jar") ? CommandLine.join(List.of("java", "-jar", path)) : CommandLine.join(List.of(path)));
        }
    }

    private void test(JButton button) {
        List<String> words;
        try {
            words = CommandLine.split(command.getText());
        } catch (IllegalArgumentException e) {
            testResult.setText(e.getMessage());
            return;
        }
        button.setEnabled(false);
        testResult.setText("Starting…");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                try (UaiClient client = UaiClient.start(words, new UaiClient.Listener() { })) {
                    UaiClient.EngineId id = client.handshake(Duration.ofSeconds(10));
                    return "OK: " + id.name() + (id.author().isEmpty() ? "" : " by " + id.author()) + ", " + id.options().size()
                            + " options";
                }
            }

            @Override
            protected void done() {
                button.setEnabled(true);
                try {
                    testResult.setText(get());
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    testResult.setText("Failed: " + cause.getMessage());
                }
            }
        }.execute();
    }

    private void accept() {
        commit();
        List<EngineConfig> engines = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < model.size(); i++) {
            EngineConfig e = model.get(i);
            if (e.name().isEmpty() || e.command().isEmpty()) {
                list.setSelectedIndex(i);
                JOptionPane.showMessageDialog(this, "Every engine needs a name and a command.", "Engines",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!names.add(e.name())) {
                list.setSelectedIndex(i);
                JOptionPane.showMessageDialog(this, "Two engines are called \"" + e.name() + "\".", "Engines",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            engines.add(e);
        }
        result = engines;
        dispose();
    }
}
