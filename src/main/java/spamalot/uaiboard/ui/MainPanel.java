package spamalot.uaiboard.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import spamalot.uaiboard.game.Game;
import spamalot.uaiboard.game.Outcome;
import spamalot.uaiboard.game.Position;
import spamalot.uaiboard.game.Side;
import spamalot.uaiboard.play.EngineConfig;
import spamalot.uaiboard.play.GameSetup;
import spamalot.uaiboard.play.PlayController;
import spamalot.uaiboard.play.Settings;
import spamalot.uaiboard.uai.UaiClient;
import spamalot.uaiboard.uai.UaiInfo;

/**
 * Everything inside the main window: the board, the clocks, the engine's analysis, the move list and the status line. It holds
 * no window of its own except the engine console, which is created when first asked for, so it can be built and painted without
 * a display.
 */
public final class MainPanel extends JPanel implements PlayController.View {
    private final Path settingsFile;
    private Settings settings;
    private final BoardPanel board = new BoardPanel(Position.start());
    private final AnalysisPanel analysis = new AnalysisPanel();
    private final JTextArea moveList = new JTextArea(12, 16);
    private final JLabel status = new JLabel(" ");
    private final JLabel xClock = new JLabel(" ", JLabel.CENTER);
    private final JLabel oClock = new JLabel(" ", JLabel.CENTER);
    private final JPanel clocks = new JPanel(new GridLayout(1, 2, 8, 0));
    private final PlayController controller;
    private final Timer timer;
    private EngineLogFrame console;
    private final java.util.ArrayDeque<String> consoleLines = new java.util.ArrayDeque<>();
    private String engineStatus = "";
    private String gameOverReason = "";

    public MainPanel(Path settingsFile) {
        super(new BorderLayout());
        this.settingsFile = settingsFile;
        this.settings = Settings.load(settingsFile);
        this.controller = new PlayController(SwingUtilities::invokeLater, this);

        for (JLabel l : new JLabel[] {xClock, oClock}) {
            l.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
            l.setOpaque(true);
            l.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        }
        clocks.add(xClock);
        clocks.add(oClock);
        clocks.setBorder(BorderFactory.createEmptyBorder(6, 8, 0, 8));

        moveList.setEditable(false);
        moveList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        JScrollPane moves = new JScrollPane(moveList);
        moves.setBorder(BorderFactory.createTitledBorder("Moves"));
        JPanel side = new JPanel(new BorderLayout(0, 6));
        side.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 8));
        side.add(analysis, BorderLayout.NORTH);
        side.add(moves, BorderLayout.CENTER);
        side.setPreferredSize(new Dimension(250, 560));
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        add(clocks, BorderLayout.NORTH);
        add(board, BorderLayout.CENTER);
        add(side, BorderLayout.EAST);
        add(status, BorderLayout.SOUTH);

        board.setMoveListener(controller::humanMove);
        timer = new Timer(100, e -> {
            controller.tick();
            updateClocks();
        });
        timer.start();
        controller.startGame(new GameSetup(null, Side.X, GameSetup.DEFAULT.timeControl()), null);
    }

    // ---- actions for the menu ----

    /** Asks for the next game's opponent, side and time control, and starts it. */
    public void newGame() {
        Settings chosen = NewGameDialog.show(this, settings);
        if (chosen == null) {
            return;
        }
        settings = chosen;
        saveSettings();
        EngineConfig engine = settings.lastSetup().vsEngine() ? settings.engine(settings.lastSetup().engineName()) : null;
        analysis.clear();
        analysis.setEngine("");
        engineStatus = "";
        gameOverReason = "";
        controller.startGame(settings.lastSetup(), engine);
    }

    public void undo() {
        controller.undo();
    }

    /** Opens the engine manager. */
    public void manageEngines() {
        java.util.List<EngineConfig> edited = EnginesDialog.show(this, settings.engines());
        if (edited != null) {
            settings = new Settings(edited, settings.lastSetup());
            saveSettings();
        }
    }

    public void showConsole() {
        if (console == null) {
            console = new EngineLogFrame();
            StringBuilder sb = new StringBuilder();
            consoleLines.forEach(sb::append);
            console.appendText(sb.toString());
        }
        console.setVisible(true);
    }

    /** Remembers a console line (the console may not exist yet) and shows it if the console is open. */
    private void log(String line) {
        consoleLines.add(line);
        while (consoleLines.size() > EngineLogFrame.MAX_LINES) {
            consoleLines.removeFirst();
        }
        if (console != null) {
            console.appendText(line);
        }
    }

    /** Stops the engine and the timers; call before the window closes. */
    public void close() {
        timer.stop();
        controller.close();
        if (console != null) {
            console.dispose();
        }
    }

    private void saveSettings() {
        try {
            settings.save(settingsFile);
        } catch (IOException e) {
            status.setText("Could not save settings: " + e.getMessage());
        }
    }

    // ---- PlayController.View: all calls arrive on the event dispatch thread ----

    @Override
    public void positionChanged() {
        Game g = controller.game();
        board.setPosition(g.position(), g.lastMove());
        board.setInputEnabled(controller.inputAllowed());
        moveList.setText(MoveListFormat.format(g.start().sideToMove(), g.start().fullmoveNumber(), g.moves()));
        moveList.setCaretPosition(moveList.getDocument().getLength());
        updateClocks();
        updateStatus();
    }

    @Override
    public void engineStatus(String text) {
        engineStatus = text;
        analysis.setEngine(text);
        updateStatus();
    }

    @Override
    public void info(UaiInfo info) {
        analysis.setInfo(info);
    }

    @Override
    public void gameOver(Outcome outcome, String reason) {
        gameOverReason = reason;
        updateStatus();
    }

    @Override
    public void problem(String message) {
        log("-- engine problem: " + message + "\n");
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                message + "\n\nThe game goes on between two people.", "Engine problem", JOptionPane.ERROR_MESSAGE));
    }

    @Override
    public void engineLine(UaiClient.Direction direction, String line) {
        // Kept even while the console is closed, so it shows what happened before it was opened.
        log(EngineLogFrame.format(direction, line));
    }

    // ---- display ----

    private void updateStatus() {
        Position p = controller.game().position();
        String counts = "x " + p.count(Side.X) + " - o " + p.count(Side.O);
        String text;
        if (controller.isOver()) {
            String result = switch (controller.outcome()) {
                case X_WINS -> "x wins";
                case O_WINS -> "o wins";
                case DRAW -> "draw";
                case ONGOING -> "";
            };
            text = "Game over: " + result + (gameOverReason.isEmpty() ? "" : " (" + gameOverReason + ")") + "    " + counts;
        } else {
            text = (p.sideToMove() == Side.X ? "x" : "o") + " to move    " + counts;
            if (!engineStatus.isEmpty()) {
                text += "    " + engineStatus;
            }
        }
        status.setText(text);
    }

    private void updateClocks() {
        long x = controller.clockMillis(Side.X);
        boolean show = x >= 0;
        clocks.setVisible(show);
        if (!show) {
            return;
        }
        long o = controller.clockMillis(Side.O);
        Side running = controller.clockRunning();
        String xName = names(Side.X);
        String oName = names(Side.O);
        paintClock(xClock, xName, x, running == Side.X);
        paintClock(oClock, oName, o, running == Side.O);
    }

    private String names(Side s) {
        GameSetup setup = controller.setup();
        boolean human = !setup.vsEngine() || setup.humanSide() == s;
        return (s == Side.X ? "x " : "o ") + (human ? "you" : "engine");
    }

    private static void paintClock(JLabel label, String who, long millis, boolean running) {
        label.setText(who + "  " + formatClock(millis));
        label.setBackground(running ? new Color(0xFFF1B8) : new Color(0xEDEDED));
        label.setForeground(millis < 10_000 ? new Color(0xB00020) : Color.BLACK);
    }

    /** {@code 4:59}, {@code 1:02:03}, and tenths below ten seconds ({@code 0:07.4}); never negative. */
    static String formatClock(long millis) {
        long ms = Math.max(0, millis);
        long totalSeconds = ms / 1000;
        long h = totalSeconds / 3600;
        long m = totalSeconds % 3600 / 60;
        long s = totalSeconds % 60;
        if (h > 0) {
            return String.format("%d:%02d:%02d", h, m, s);
        }
        if (ms < 10_000) {
            return String.format("%d:%02d.%d", m, s, ms % 1000 / 100);
        }
        return String.format("%d:%02d", m, s);
    }

    /** For tests and screenshots: the board's component. */
    BoardPanel board() {
        return board;
    }

    PlayController controller() {
        return controller;
    }
}
