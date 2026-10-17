package spamalot.uaiboard.ui;

import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import spamalot.uaiboard.play.Settings;

/** The application window: a menu bar around a {@link MainPanel}. */
public final class MainFrame extends JFrame {
    private final MainPanel panel = new MainPanel(Settings.defaultFile());

    public MainFrame() {
        super("uai-board");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                panel.close();
            }
        });
        setJMenuBar(buildMenu());
        setContentPane(panel);
        pack();
        setLocationByPlatform(true);
    }

    private static KeyStroke shortcut(int key) {
        return KeyStroke.getKeyStroke(key, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
    }

    private JMenuBar buildMenu() {
        JMenuBar bar = new JMenuBar();

        JMenu game = new JMenu("Game");
        game.setMnemonic(KeyEvent.VK_G);
        JMenuItem newGame = new JMenuItem("New Game…");
        newGame.setAccelerator(shortcut(KeyEvent.VK_N));
        newGame.addActionListener(e -> panel.newGame());
        JMenuItem undo = new JMenuItem("Undo");
        undo.setAccelerator(shortcut(KeyEvent.VK_Z));
        undo.addActionListener(e -> panel.undo());
        JMenuItem quit = new JMenuItem("Quit");
        quit.setAccelerator(shortcut(KeyEvent.VK_Q));
        quit.addActionListener(e -> {
            panel.close();
            dispose();
            System.exit(0);
        });
        game.add(newGame);
        game.add(undo);
        game.addSeparator();
        game.add(quit);

        JMenu engine = new JMenu("Engine");
        engine.setMnemonic(KeyEvent.VK_E);
        JMenuItem engines = new JMenuItem("Engines…");
        engines.addActionListener(e -> panel.manageEngines());
        JMenuItem console = new JMenuItem("Show Console");
        console.addActionListener(e -> panel.showConsole());
        engine.add(engines);
        engine.add(console);

        JMenu help = new JMenu("Help");
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "uai-board\nA Swing front end for Ataxx engines that speak UAI.\nMIT licence.", "About uai-board",
                JOptionPane.INFORMATION_MESSAGE));
        help.add(about);

        bar.add(game);
        bar.add(engine);
        bar.add(help);
        return bar;
    }
}
