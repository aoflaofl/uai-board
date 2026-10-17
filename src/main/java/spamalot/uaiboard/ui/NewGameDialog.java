package spamalot.uaiboard.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import spamalot.uaiboard.game.Position;
import spamalot.uaiboard.game.Side;
import spamalot.uaiboard.play.EngineConfig;
import spamalot.uaiboard.play.GameSetup;
import spamalot.uaiboard.play.Settings;
import spamalot.uaiboard.play.TimeControl;

/** Asks who plays the next game: two people, or a person against one of the configured engines, with its time limit. */
public final class NewGameDialog extends JDialog {
    private static final String HUMAN = "Another person (two players)";

    private final Component parent;
    private List<EngineConfig> engines;
    private final JComboBox<String> opponent = new JComboBox<>();
    private final JRadioButton playX = new JRadioButton("x (moves first)");
    private final JRadioButton playO = new JRadioButton("o");
    private final JRadioButton perMove = new JRadioButton("Fixed time per move");
    private final JRadioButton clock = new JRadioButton("Game clock");
    private final JSpinner moveSeconds = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 3600.0, 0.5));
    private final JSpinner baseSeconds = new JSpinner(new SpinnerNumberModel(300, 1, 36000, 30));
    private final JSpinner incrementSeconds = new JSpinner(new SpinnerNumberModel(2, 0, 600, 1));
    private final JTextField fen = new JTextField(30);
    private final JLabel fenProblem = new JLabel(" ");
    private Result result;

    /** What the user asked for: the settings to keep, and the position to start from. */
    public record Result(Settings settings, Position start) { }

    private NewGameDialog(Component parent, Settings settings) {
        super(SwingUtilities.getWindowAncestor(parent), "New game", ModalityType.APPLICATION_MODAL);
        this.parent = parent;
        this.engines = new ArrayList<>(settings.engines());
        fillOpponents(settings.lastSetup().engineName());

        ButtonGroup side = new ButtonGroup();
        side.add(playX);
        side.add(playO);
        (settings.lastSetup().humanSide() == Side.X ? playX : playO).setSelected(true);
        ButtonGroup tc = new ButtonGroup();
        tc.add(perMove);
        tc.add(clock);
        TimeControl last = settings.lastSetup().timeControl();
        if (last instanceof TimeControl.Clock c) {
            clock.setSelected(true);
            baseSeconds.setValue((int) (c.baseMillis() / 1000));
            incrementSeconds.setValue((int) (c.incrementMillis() / 1000));
        } else {
            perMove.setSelected(true);
            moveSeconds.setValue(((TimeControl.MoveTime) last).millis() / 1000.0);
        }
        opponent.addActionListener(e -> enableControls());
        perMove.addActionListener(e -> enableControls());
        clock.addActionListener(e -> enableControls());

        JButton manage = new JButton("Engines…");
        manage.addActionListener(e -> {
            List<EngineConfig> edited = EnginesDialog.show(this, engines);
            if (edited != null) {
                engines = new ArrayList<>(edited);
                fillOpponents((String) opponent.getSelectedItem());
            }
        });

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        c.gridx = 0;
        form.add(new JLabel("Opponent"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(opponent, c);
        c.gridx = 2;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(manage, c);
        c.gridy = 1;
        c.gridx = 0;
        form.add(new JLabel("You play"), c);
        JPanel sides = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        sides.add(playX);
        sides.add(playO);
        c.gridx = 1;
        c.gridwidth = 2;
        form.add(sides, c);
        c.gridy = 2;
        c.gridx = 0;
        c.gridwidth = 1;
        form.add(new JLabel("Engine time"), c);
        JPanel moveRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        moveRow.add(perMove);
        moveRow.add(moveSeconds);
        moveRow.add(new JLabel("seconds"));
        c.gridx = 1;
        c.gridwidth = 2;
        form.add(moveRow, c);
        c.gridy = 3;
        JPanel clockRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        clockRow.add(clock);
        clockRow.add(baseSeconds);
        clockRow.add(new JLabel("s + "));
        clockRow.add(incrementSeconds);
        clockRow.add(new JLabel("s per move"));
        form.add(clockRow, c);

        c.gridy = 4;
        c.gridx = 0;
        c.gridwidth = 1;
        form.add(new JLabel("Start position"), c);
        fen.setToolTipText("A FEN such as x5o/7/7/7/7/7/o5x x 0 1; leave empty for the standard start");
        c.gridx = 1;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(fen, c);
        c.gridy = 5;
        fenProblem.setForeground(new java.awt.Color(0xB00020));
        form.add(fenProblem, c);

        JButton ok = new JButton("Start");
        ok.addActionListener(e -> accept());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ok);
        buttons.add(cancel);
        getRootPane().setDefaultButton(ok);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        enableControls();
        pack();
        setLocationRelativeTo(parent);
    }

    /**
     * Shows the dialog. Returns the settings to use (the engine list as edited, and the chosen setup as the last setup) and the
     * start position, or null if the user cancelled.
     */
    public static Result show(Component parent, Settings settings) {
        NewGameDialog d = new NewGameDialog(parent, settings);
        d.setVisible(true);
        return d.result;
    }

    private void fillOpponents(String select) {
        opponent.removeAllItems();
        opponent.addItem(HUMAN);
        for (EngineConfig e : engines) {
            opponent.addItem(e.name());
        }
        opponent.setSelectedItem(select != null ? select : HUMAN);
        if (opponent.getSelectedItem() == null) {
            opponent.setSelectedItem(HUMAN);
        }
    }

    private boolean engineChosen() {
        return opponent.getSelectedItem() != null && !HUMAN.equals(opponent.getSelectedItem());
    }

    private void enableControls() {
        boolean engine = engineChosen();
        playX.setEnabled(engine);
        playO.setEnabled(engine);
        perMove.setEnabled(engine);
        clock.setEnabled(engine);
        moveSeconds.setEnabled(engine && perMove.isSelected());
        baseSeconds.setEnabled(engine && clock.isSelected());
        incrementSeconds.setEnabled(engine && clock.isSelected());
    }

    private void accept() {
        Position start = Position.start();
        String text = fen.getText().strip();
        if (!text.isEmpty()) {
            try {
                start = Position.fromFen(text);
            } catch (RuntimeException e) {
                fenProblem.setText("Not a valid FEN: " + e.getMessage());
                pack();
                return;
            }
        }
        TimeControl tc = clock.isSelected()
                ? new TimeControl.Clock(((Number) baseSeconds.getValue()).longValue() * 1000,
                        ((Number) incrementSeconds.getValue()).longValue() * 1000)
                : new TimeControl.MoveTime(Math.max(1, Math.round(((Number) moveSeconds.getValue()).doubleValue() * 1000)));
        String engine = engineChosen() ? (String) opponent.getSelectedItem() : null;
        result = new Result(new Settings(engines, new GameSetup(engine, playO.isSelected() ? Side.O : Side.X, tc)), start);
        dispose();
    }
}
