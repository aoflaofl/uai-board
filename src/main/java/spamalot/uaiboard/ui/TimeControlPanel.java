package spamalot.uaiboard.ui;

import java.awt.FlowLayout;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import spamalot.uaiboard.play.TimeControl;

/** Chooses a time control: a fixed time per move, or a game clock with a base time and an increment. */
public final class TimeControlPanel extends JPanel {
    private final JRadioButton perMove = new JRadioButton("Fixed time per move");
    private final JRadioButton clock = new JRadioButton("Game clock");
    private final JSpinner moveSeconds = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 3600.0, 0.5));
    private final JSpinner baseSeconds = new JSpinner(new SpinnerNumberModel(300, 1, 36000, 30));
    private final JSpinner incrementSeconds = new JSpinner(new SpinnerNumberModel(2, 0, 600, 1));

    public TimeControlPanel(TimeControl initial) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        ButtonGroup group = new ButtonGroup();
        group.add(perMove);
        group.add(clock);
        JPanel moveRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        moveRow.add(perMove);
        moveRow.add(moveSeconds);
        moveRow.add(new JLabel("seconds"));
        JPanel clockRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        clockRow.add(clock);
        clockRow.add(baseSeconds);
        clockRow.add(new JLabel("s + "));
        clockRow.add(incrementSeconds);
        clockRow.add(new JLabel("s per move"));
        add(moveRow);
        add(clockRow);
        perMove.addActionListener(e -> updateEnabled(isEnabled()));
        clock.addActionListener(e -> updateEnabled(isEnabled()));
        setTimeControl(initial);
    }

    /** The time control as chosen. */
    public TimeControl timeControl() {
        if (clock.isSelected()) {
            return new TimeControl.Clock(((Number) baseSeconds.getValue()).longValue() * 1000,
                    ((Number) incrementSeconds.getValue()).longValue() * 1000);
        }
        return new TimeControl.MoveTime(Math.max(1, Math.round(((Number) moveSeconds.getValue()).doubleValue() * 1000)));
    }

    public void setTimeControl(TimeControl tc) {
        if (tc instanceof TimeControl.Clock c) {
            clock.setSelected(true);
            baseSeconds.setValue((int) Math.max(1, c.baseMillis() / 1000));
            incrementSeconds.setValue((int) (c.incrementMillis() / 1000));
        } else {
            perMove.setSelected(true);
            moveSeconds.setValue(Math.max(0.01, ((TimeControl.MoveTime) tc).millis() / 1000.0));
        }
        updateEnabled(isEnabled());
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        perMove.setEnabled(enabled);
        clock.setEnabled(enabled);
        updateEnabled(enabled);
    }

    private void updateEnabled(boolean enabled) {
        moveSeconds.setEnabled(enabled && perMove.isSelected());
        baseSeconds.setEnabled(enabled && clock.isSelected());
        incrementSeconds.setEnabled(enabled && clock.isSelected());
    }
}
