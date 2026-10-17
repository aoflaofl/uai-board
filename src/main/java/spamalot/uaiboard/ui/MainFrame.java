package spamalot.uaiboard.ui;

import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.ButtonGroup;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
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
        JMenuItem save = new JMenuItem("Save Game…");
        save.setAccelerator(shortcut(KeyEvent.VK_S));
        save.addActionListener(e -> panel.saveGame());
        JMenuItem load = new JMenuItem("Load Game…");
        load.setAccelerator(shortcut(KeyEvent.VK_O));
        load.addActionListener(e -> panel.loadGame());
        JMenuItem resign = new JMenuItem("Resign…");
        resign.addActionListener(e -> panel.resign());
        JMenuItem draw = new JMenuItem("Draw by Agreement…");
        draw.addActionListener(e -> panel.agreeDraw());
        // Resigning and drawing only make sense while a game is on (and a draw only between two people).
        game.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                resign.setEnabled(panel.canResign());
                draw.setEnabled(panel.canAgreeDraw());
            }

            @Override
            public void menuDeselected(MenuEvent e) { }

            @Override
            public void menuCanceled(MenuEvent e) { }
        });
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
        game.add(save);
        game.add(load);
        game.addSeparator();
        game.add(resign);
        game.add(draw);
        game.addSeparator();
        game.add(quit);

        JMenu view = new JMenu("View");
        view.setMnemonic(KeyEvent.VK_V);
        JCheckBoxMenuItem flip = new JCheckBoxMenuItem("Flip Board");
        flip.setAccelerator(shortcut(KeyEvent.VK_F));
        flip.addActionListener(e -> panel.setFlipped(flip.isSelected()));
        panel.setFlipListener(flip::setSelected);
        view.add(flip);

        JMenu engine = new JMenu("Engine");
        engine.setMnemonic(KeyEvent.VK_E);
        JMenuItem engines = new JMenuItem("Engines…");
        engines.addActionListener(e -> panel.manageEngines());
        JMenuItem console = new JMenuItem("Show Console");
        console.addActionListener(e -> panel.showConsole());
        JCheckBoxMenuItem analyse = new JCheckBoxMenuItem("Analyse Position");
        analyse.setAccelerator(shortcut(KeyEvent.VK_L));
        analyse.addActionListener(e -> panel.setAnalysisEnabled(analyse.isSelected()));
        panel.setAnalysisListener(analyse::setSelected);
        JMenu analysisEngine = new JMenu("Analysis Engine");
        // The list of engines can change, so the choices are rebuilt each time the menu is opened.
        analysisEngine.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                analysisEngine.removeAll();
                ButtonGroup group = new ButtonGroup();
                String chosen = panel.analysisEngineName();
                for (String name : panel.analysisEngineNames()) {
                    JRadioButtonMenuItem item = new JRadioButtonMenuItem(name, name.equals(chosen));
                    item.addActionListener(ev -> panel.chooseAnalysisEngine(name));
                    group.add(item);
                    analysisEngine.add(item);
                }
                if (analysisEngine.getItemCount() == 0) {
                    JMenuItem none = new JMenuItem("(no engines: use Engines…)");
                    none.setEnabled(false);
                    analysisEngine.add(none);
                }
            }

            @Override
            public void menuDeselected(MenuEvent e) { }

            @Override
            public void menuCanceled(MenuEvent e) { }
        });
        engine.add(analyse);
        engine.add(analysisEngine);
        engine.addSeparator();
        JMenuItem match = new JMenuItem("Engine Match…");
        match.addActionListener(e -> panel.openMatch());
        engine.add(match);
        engine.addSeparator();
        engine.add(engines);
        engine.add(console);

        JMenu help = new JMenu("Help");
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "uai-board\nA Swing front end for Ataxx engines that speak UAI.\nMIT licence.", "About uai-board",
                JOptionPane.INFORMATION_MESSAGE));
        help.add(about);

        bar.add(game);
        bar.add(view);
        bar.add(engine);
        bar.add(help);
        return bar;
    }
}
