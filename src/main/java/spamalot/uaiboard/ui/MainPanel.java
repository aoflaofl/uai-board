package spamalot.uaiboard.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import spamalot.uaiboard.game.Game;
import spamalot.uaiboard.game.GameRecord;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Outcome;
import spamalot.uaiboard.game.Position;
import spamalot.uaiboard.game.Side;
import spamalot.uaiboard.play.Analyser;
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
    private final AnalysisPanel analysisView = new AnalysisPanel("Analysis", true);
    private final Analyser analyser;
    private boolean analysing;
    private Consumer<Boolean> analysisListener = on -> { };
    private final JTextArea moveList = new JTextArea(12, 16);
    private final JLabel status = new JLabel(" ");
    private final JLabel xClock = new JLabel(" ", JLabel.CENTER);
    private final JLabel oClock = new JLabel(" ", JLabel.CENTER);
    private final JPanel clocks = new JPanel(new GridLayout(1, 2, 8, 0));
    private final PlayController controller;
    private final Timer timer;
    private EngineLogFrame console;
    private MatchFrame matchFrame;
    private final java.util.ArrayDeque<String> consoleLines = new java.util.ArrayDeque<>();
    private String engineStatus = "";
    private String gameOverReason = "";
    private boolean flipped;
    private final java.util.List<Move> redoMoves = new java.util.ArrayList<>();
    private String loadedDescription = "";
    private java.util.function.Function<java.util.List<GameRecord>, Integer> gameChooser = this::askWhichGame;
    private Consumer<Boolean> flipListener = f -> { };

    public MainPanel(Path settingsFile) {
        super(new BorderLayout());
        this.settingsFile = settingsFile;
        this.settings = Settings.load(settingsFile);
        this.controller = new PlayController(SwingUtilities::invokeLater, this);
        this.analyser = new Analyser(SwingUtilities::invokeLater, analyserListener());

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
        JPanel panels = new JPanel();
        panels.setLayout(new javax.swing.BoxLayout(panels, javax.swing.BoxLayout.Y_AXIS));
        panels.add(analysis);
        panels.add(analysisView);
        analysisView.setVisible(false);
        side.add(panels, BorderLayout.NORTH);
        side.add(moves, BorderLayout.CENTER);
        side.setPreferredSize(new Dimension(250, 560));
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        add(clocks, BorderLayout.NORTH);
        add(board, BorderLayout.CENTER);
        add(side, BorderLayout.EAST);
        add(status, BorderLayout.SOUTH);

        board.setMoveListener(this::humanMove);
        timer = new Timer(100, e -> {
            controller.tick();
            updateClocks();
        });
        timer.start();
        controller.startGame(new GameSetup(null, Side.X, GameSetup.DEFAULT.timeControl()), null);
    }

    // ---- actions for the menu ----

    /** Asks for the next game's opponent, side, time control and start position, and starts it. */
    public void newGame() {
        NewGameDialog.Result chosen = NewGameDialog.show(this, settings);
        if (chosen == null) {
            return;
        }
        settings = chosen.settings();
        saveSettings();
        GameSetup setup = settings.lastSetup();
        EngineConfig engine = setup.vsEngine() ? settings.engine(setup.engineName()) : null;
        resetDisplay();
        // Seen from the person's side: when they play o against an engine the board is turned around.
        setFlipped(engine != null && setup.humanSide() == Side.O);
        controller.startGame(setup, engine, chosen.start());
    }

    private void resetDisplay() {
        redoMoves.clear();
        loadedDescription = "";
        analysis.clear();
        analysis.setEngine("");
        engineStatus = "";
        gameOverReason = "";
    }

    /** Shows the board from the other side. */
    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
        board.setFlipped(flipped);
        flipListener.accept(flipped);
    }

    public boolean isFlipped() {
        return flipped;
    }

    /** Told whenever the board is flipped (so a menu check box can follow). */
    public void setFlipListener(Consumer<Boolean> listener) {
        this.flipListener = listener;
    }

    public boolean canResign() {
        return controller.canResign();
    }

    public boolean canAgreeDraw() {
        return controller.canAgreeDraw();
    }

    public void resign() {
        if (controller.canResign() && JOptionPane.showConfirmDialog(this, "Resign the game?", "Resign",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            controller.resign();
        }
    }

    public void agreeDraw() {
        if (controller.canAgreeDraw() && JOptionPane.showConfirmDialog(this, "Do both players agree to a draw?",
                "Draw by agreement", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            controller.agreeDraw();
        }
    }

    /** The game as text, with the names of the players and today's date. */
    String gameText() {
        GameSetup setup = controller.setup();
        String opponent = controller.opponentName();
        String person = System.getProperty("user.name", "Player");
        String xName = opponent == null ? "x" : setup.humanSide() == Side.X ? person : opponent;
        String oName = opponent == null ? "o" : setup.humanSide() == Side.O ? person : opponent;
        return GameRecord.of(controller.game(), xName, oName, GameRecord.resultTag(controller.outcome()), LocalDate.now())
                .toText();
    }

    /** Writes the game to {@code file}; returns an error message, or null on success. */
    String saveTo(Path file) {
        try {
            Files.writeString(file, gameText());
            return null;
        } catch (IOException e) {
            return "Could not save the game: " + e.getMessage();
        }
    }

    /** Reads a game from {@code file} and continues it as a game between two people; returns an error message, or null. */
    String loadFrom(Path file) {
        java.util.List<GameRecord> records;
        try {
            records = GameRecord.parseAll(Files.readString(file));
        } catch (IOException | RuntimeException e) {
            return "Could not load the game: " + e.getMessage();
        }
        int chosen = 0;
        if (records.size() > 1) {
            chosen = gameChooser.apply(records);
            if (chosen < 0) {
                return null; // the user cancelled
            }
        }
        loadRecord(records.get(chosen));
        return null;
    }

    /** Continues {@code record} as a game between two people, from its last position. */
    void loadRecord(GameRecord record) {
        resetDisplay();
        setFlipped(false);
        loadedDescription = describe(record);
        controller.loadGame(record.toGame());
    }

    /** Opens {@code record} for review: the board shows its start, and Redo (or Go to End) steps through the moves. */
    public void reviewRecord(GameRecord record) {
        loadRecord(record);
        goToStart();
        java.awt.Window w = SwingUtilities.getWindowAncestor(this);
        if (w != null) {
            w.toFront();
        }
    }

    /** {@code gataxx vs Moonbird, round 3, 1-0 (game over)} from a record's tags; empty if it has none of them. */
    static String describe(GameRecord r) {
        StringBuilder sb = new StringBuilder();
        if (!r.tag("X").isEmpty() || !r.tag("O").isEmpty()) {
            sb.append("x ").append(r.tag("X")).append(" vs o ").append(r.tag("O"));
        }
        if (!r.tag("Round").isEmpty()) {
            sb.append(sb.length() > 0 ? ", " : "").append("round ").append(r.tag("Round"));
        }
        String result = r.tag("Result");
        if (!result.isEmpty() && !result.equals("*")) {
            sb.append(sb.length() > 0 ? ", " : "").append(result);
            if (!r.tag("Termination").isEmpty() && !r.tag("Termination").equals("game over")) {
                sb.append(" (").append(r.tag("Termination")).append(')');
            }
        }
        return sb.toString();
    }

    /** One line per game for the list the user picks from. */
    static String listLabel(int number, GameRecord r) {
        String d = describe(r);
        return number + ". " + (d.isEmpty() ? "game" : d) + "  [" + r.moves().size() + " moves]";
    }

    /** Shows the games of a file and returns the index of the one the user picks, or -1 if they cancel. */
    private int askWhichGame(java.util.List<GameRecord> records) {
        String[] labels = new String[records.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = listLabel(i + 1, records.get(i));
        }
        javax.swing.JList<String> list = new javax.swing.JList<>(labels);
        list.setSelectedIndex(0);
        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(list);
        scroll.setPreferredSize(new Dimension(560, 300));
        int answer = JOptionPane.showConfirmDialog(this, scroll, "This file holds " + records.size() + " games: choose one",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return answer == JOptionPane.OK_OPTION ? Math.max(0, list.getSelectedIndex()) : -1;
    }

    /** For tests: replaces the question asked when a file holds several games. */
    void setGameChooser(java.util.function.Function<java.util.List<GameRecord>, Integer> chooser) {
        this.gameChooser = chooser;
    }

    /** Asks for a file and saves the game there. */
    public void saveGame() {
        javax.swing.JFileChooser chooser = gameChooser();
        chooser.setSelectedFile(new java.io.File("game-" + LocalDate.now() + ".ataxx"));
        if (chooser.showSaveDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            java.io.File f = chooser.getSelectedFile();
            if (f.exists() && JOptionPane.showConfirmDialog(this, f.getName() + " exists. Replace it?", "Save game",
                    JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
                return;
            }
            String problem = saveTo(f.toPath());
            if (problem != null) {
                JOptionPane.showMessageDialog(this, problem, "Save game", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /** Asks for a file and loads the game in it. */
    public void loadGame() {
        javax.swing.JFileChooser chooser = gameChooser();
        if (chooser.showOpenDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            String problem = loadFrom(chooser.getSelectedFile().toPath());
            if (problem != null) {
                JOptionPane.showMessageDialog(this, problem, "Load game", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static javax.swing.JFileChooser gameChooser() {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Ataxx games (*.ataxx)", "ataxx"));
        return chooser;
    }

    // ---- analysis mode ----

    private Analyser.Listener analyserListener() {
        return new Analyser.Listener() {
            @Override
            public void status(String text) {
                analysisView.setEngine(text);
            }

            @Override
            public void info(UaiInfo info, Side sideToMove) {
                analysisView.setInfo(info, sideToMove);
            }

            @Override
            public void problem(String message) {
                setAnalysing(false);
                log("-- analysis engine problem: " + message + "\n");
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(MainPanel.this,
                        message + "\n\nAnalysis is off.", "Analysis engine problem", JOptionPane.ERROR_MESSAGE));
            }

            @Override
            public void line(UaiClient.Direction direction, String line) {
                log("[analysis] " + EngineLogFrame.format(direction, line));
            }
        };
    }

    private void setAnalysing(boolean on) {
        analysing = on;
        analysisView.setVisible(on);
        if (!on) {
            analysisView.clear();
            analysisView.setEngine("");
        }
        analysisListener.accept(on);
    }

    /** Told whenever analysis mode turns on or off (so a menu check box can follow). */
    public void setAnalysisListener(Consumer<Boolean> listener) {
        this.analysisListener = listener;
    }

    public boolean isAnalysing() {
        return analysing;
    }

    /** Names of the engines that can be chosen for analysis. */
    public java.util.List<String> analysisEngineNames() {
        return settings.engines().stream().map(EngineConfig::name).toList();
    }

    /** The engine chosen for analysis: the saved choice, else the first engine; null if there are no engines. */
    public String analysisEngineName() {
        EngineConfig e = analysisEngine();
        return e == null ? null : e.name();
    }

    private EngineConfig analysisEngine() {
        EngineConfig saved = settings.analysisEngine() == null ? null : settings.engine(settings.analysisEngine());
        return saved != null ? saved : settings.engines().isEmpty() ? null : settings.engines().get(0);
    }

    /** Turns analysis mode on or off. Without a configured engine it explains what to do and stays off. */
    public void setAnalysisEnabled(boolean on) {
        if (!on) {
            analyser.stop();
            setAnalysing(false);
            return;
        }
        EngineConfig engine = analysisEngine();
        if (engine == null) {
            setAnalysing(false);
            JOptionPane.showMessageDialog(this, "Add an engine first (Engine > Engines…).", "Analysis",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        setAnalysing(true);
        Game g = controller.game();
        analyser.analyse(g.start(), g.moves());
        analyser.start(engine);
    }

    /** Chooses the engine used for analysis (restarting the analysis if it is on). */
    public void chooseAnalysisEngine(String name) {
        settings = settings.withAnalysisEngine(name);
        saveSettings();
        if (analysing) {
            analyser.start(analysisEngine());
        }
    }

    /** A person's move: it ends any stepping forward through taken-back moves. */
    void humanMove(Move move) {
        redoMoves.clear();
        controller.humanMove(move);
    }

    public void undo() {
        java.util.List<Move> before = controller.game().moves();
        controller.undo();
        java.util.List<Move> after = controller.game().moves();
        if (controller.setup().vsEngine()) {
            redoMoves.clear(); // an engine would answer, so there is nothing to step forward through
            return;
        }
        // Remember what was taken back (passes come back by themselves) so that Redo can play it again.
        java.util.List<Move> taken = new java.util.ArrayList<>();
        for (Move m : before.subList(after.size(), before.size())) {
            if (!m.isPass()) {
                taken.add(m);
            }
        }
        redoMoves.addAll(0, taken);
    }

    public boolean canRedo() {
        return !redoMoves.isEmpty() && !controller.setup().vsEngine();
    }

    /** Plays again the move that Undo took back. */
    public void redo() {
        if (canRedo()) {
            controller.humanMove(redoMoves.remove(0));
        }
    }

    /** Takes back every move (Redo then steps forward). */
    public void goToStart() {
        while (!controller.game().moves().isEmpty() && !controller.setup().vsEngine()) {
            int before = controller.game().moves().size();
            undo();
            if (controller.game().moves().size() >= before) {
                break;
            }
        }
    }

    /** Plays again every move that was taken back. */
    public void goToEnd() {
        while (canRedo()) {
            int before = redoMoves.size();
            redo();
            if (redoMoves.size() >= before) {
                break;
            }
        }
    }

    /** Opens the engine manager. */
    public void manageEngines() {
        java.util.List<EngineConfig> edited = EnginesDialog.show(this, settings.engines());
        if (edited != null) {
            settings = settings.withEngines(edited);
            saveSettings();
            if (matchFrame != null && matchFrame.isDisplayable()) {
                matchFrame.setEngines(edited);
            }
        }
    }

    /** Opens the engine match window (or brings it to the front if it is open). */
    public void openMatch() {
        if (matchFrame != null && matchFrame.isDisplayable()) {
            matchFrame.toFront();
            return;
        }
        matchFrame = new MatchFrame(this, settings.engines(), settings.matchPrefs(), this::reviewRecord, prefs -> {
            settings = settings.withMatchPrefs(prefs);
            saveSettings();
        }, edited -> {
            settings = settings.withEngines(edited);
            saveSettings();
        });
        matchFrame.setVisible(true);
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
        analyser.close();
        controller.close();
        if (matchFrame != null) {
            matchFrame.shutDown();
        }
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
        analysis.setVisible(controller.setup().vsEngine());
        if (analysing) {
            analyser.analyse(g.start(), g.moves());
        }
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
        if (!loadedDescription.isEmpty() && !controller.setup().vsEngine()) {
            text += "    " + loadedDescription;
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

    String statusText() {
        return status.getText();
    }

    /** For tests and screenshots: the board's component. */
    BoardPanel board() {
        return board;
    }

    PlayController controller() {
        return controller;
    }
}
