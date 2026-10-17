package spamalot.uaiboard.ui;

import java.awt.Component;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import spamalot.uaiboard.play.EngineConfig;
import spamalot.uaiboard.play.MatchPrefs;

/** The engine match window: a {@link MatchPanel} in a frame that asks before it stops a running match. */
public final class MatchFrame extends JFrame {
    private final MatchPanel panel;

    public MatchFrame(Component owner, List<EngineConfig> engines, MatchPrefs prefs, Consumer<MatchPrefs> savePrefs,
                      Consumer<List<EngineConfig>> saveEngines) {
        super("uai-board: engine match");
        this.panel = new MatchPanel(engines, prefs, savePrefs, saveEngines);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (panel.isRunning() && JOptionPane.showConfirmDialog(MatchFrame.this,
                        "A match is running. Stop it and close the window?", "Engine match",
                        JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
                    return;
                }
                panel.close();
                dispose();
            }
        });
        setContentPane(panel);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Replaces the engine list shown in the window. */
    public void setEngines(List<EngineConfig> engines) {
        panel.setEngines(engines);
    }

    /** Stops any running match and closes the window (no question asked). */
    public void shutDown() {
        panel.close();
        dispose();
    }
}
