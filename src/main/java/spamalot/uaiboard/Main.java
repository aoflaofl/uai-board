package spamalot.uaiboard;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import spamalot.uaiboard.ui.MainFrame;

/** Starts uai-board. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // The default look and feel is fine.
            }
            new MainFrame().setVisible(true);
        });
    }
}
