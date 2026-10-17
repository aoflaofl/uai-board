package spamalot.uaiboard.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ButtonGroup;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import spamalot.uaiboard.game.Game;
import spamalot.uaiboard.game.GameRecord;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Position;
import spamalot.uaiboard.game.Side;
import spamalot.uaiboard.match.MatchController;
import spamalot.uaiboard.match.MatchGame;
import spamalot.uaiboard.match.MatchReport;
import spamalot.uaiboard.match.MatchSetup;
import spamalot.uaiboard.match.Openings;
import spamalot.uaiboard.match.Standings;
import spamalot.uaiboard.match.StandingsText;
import spamalot.uaiboard.play.EngineConfig;
import spamalot.uaiboard.play.MatchPrefs;
import spamalot.uaiboard.play.TimeControl;
import spamalot.uaiboard.uai.UaiInfo;

/**
 * The content of the engine match window: the setup (two engines, time control, games, openings), the running game on a board
 * with each engine's analysis and the clocks, the score, and the list of finished games.
 *
 * <p>The match runs on its own thread and reports from there; the board, the analysis panels, the clocks and the console are
 * updated from the latest values about ten times a second (so fast games cannot flood the event dispatch thread), the rest of
 * the events are passed on to it one by one. It needs no display to be built.
 */
public final class MatchPanel extends JPanel {
    private static final Color RUNNING_BACKGROUND = new Color(0xFFF1B8);
    private static final Color IDLE_BACKGROUND = new Color(0xEDEDED);

    private List<EngineConfig> engines;
    private final Consumer<GameRecord> reviewHandler;
    private final Consumer<MatchPrefs> savePrefs;
    private final Consumer<List<EngineConfig>> saveEngines;

    // setup
    private final JComboBox<String> engineA = new JComboBox<>();
    private final JComboBox<String> engineB = new JComboBox<>();
    private final JTextArea extraA = new JTextArea(2, 20);
    private final JTextArea extraB = new JTextArea(2, 20);
    private final TimeControlPanel timeControl;
    private final JSpinner games = new JSpinner(new SpinnerNumberModel(20, 1, 1_000_000, 2));
    private final JSpinner grace = new JSpinner(new SpinnerNumberModel(1000, 0, 600_000, 100));
    private final JRadioButton randomOpenings = new JRadioButton("Random, plies");
    private final JRadioButton fileOpenings = new JRadioButton("From file");
    private final JSpinner plies = new JSpinner(new SpinnerNumberModel(6, 0, 100, 1));
    private final JSpinner seed = new JSpinner(new SpinnerNumberModel(1, Integer.MIN_VALUE, Integer.MAX_VALUE, 1));
    private final JTextField openingFile = new JTextField(14);
    private final JButton browse = new JButton("Browse…");
    private final JCheckBox live = new JCheckBox("Show the games live", true);
    private final JButton start = new JButton("Start");
    private final JButton pause = new JButton("Pause after game");
    private final JButton stop = new JButton("Stop");
    private final JButton editEngines = new JButton("Edit engines…");
    private final JButton console = new JButton("Console");
    private final JButton saveResults = new JButton("Save results…");
    private final java.util.List<Component> setupControls = new java.util.ArrayList<>();

    // display
    private final BoardPanel board = new BoardPanel(Position.start());
    private final JLabel xPlayer = new JLabel(" ", JLabel.CENTER);
    private final JLabel oPlayer = new JLabel(" ", JLabel.CENTER);
    private final AnalysisPanel analysisA = new AnalysisPanel("Engine A", false);
    private final AnalysisPanel analysisB = new AnalysisPanel("Engine B", false);
    private final JLabel gameStatus = new JLabel(" ");
    private final JLabel standingsLabel = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar(0, 1);
    private final DefaultTableModel table = new DefaultTableModel(
            new String[] {"#", "x", "o", "Result", "Ended by", "Plies", "A"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final Timer timer;
    private EngineLogFrame consoleFrame;

    // what the match thread reports; read by the timer on the event dispatch thread
    private record Frame(Position position, Move lastMove) { }

    private record ClockState(Side toMove, long xRemainingMs, long oRemainingMs, long nanoStamp) { }

    private final AtomicReference<Frame> frame = new AtomicReference<>();
    private final AtomicReference<UaiInfo> infoA = new AtomicReference<>();
    private final AtomicReference<UaiInfo> infoB = new AtomicReference<>();
    private final AtomicReference<ClockState> clockState = new AtomicReference<>();
    private volatile boolean aIsX = true;
    private volatile boolean showLive = true;
    private final ArrayDeque<String> consoleHistory = new ArrayDeque<>();
    private long consoleTotal;
    private long consoleShown;

    private MatchController controller;
    private MatchSetup setup;
    private final List<MatchGame> finishedGames = new java.util.ArrayList<>();
    private String xName = "";
    private String oName = "";
    private boolean pausing;

    public MatchPanel(List<EngineConfig> engines, MatchPrefs prefs, Consumer<GameRecord> reviewHandler,
                      Consumer<MatchPrefs> savePrefs, Consumer<List<EngineConfig>> saveEngines) {
        super(new BorderLayout(8, 8));
        this.engines = List.copyOf(engines);
        this.reviewHandler = reviewHandler;
        this.savePrefs = savePrefs;
        this.saveEngines = saveEngines;
        this.timeControl = new TimeControlPanel(prefs.timeControl());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(new JScrollPane(buildSetup(prefs), JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.WEST);
        add(buildBoardArea(), BorderLayout.CENTER);
        add(buildAnalysis(), BorderLayout.EAST);
        add(buildBottom(), BorderLayout.SOUTH);
        refillEngines(prefs.engineA(), prefs.engineB());
        enableSetup(true);
        updateButtons();
        showStandings(Standings.empty());

        timer = new Timer(100, e -> flush());
        timer.start();
    }

    // ---- layout ----

    private JPanel buildSetup(MatchPrefs prefs) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(engineBox("Engine A", engineA, extraA, prefs.extraA()));
        p.add(engineBox("Engine B", engineB, extraB, prefs.extraB()));

        JPanel time = new JPanel(new BorderLayout());
        time.setBorder(BorderFactory.createTitledBorder("Time control"));
        time.add(timeControl, BorderLayout.CENTER);
        setupControls.add(timeControl);
        p.add(time);

        JPanel count = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        count.setBorder(BorderFactory.createTitledBorder("Games"));
        games.setValue(prefs.games());
        grace.setValue((int) prefs.graceMs());
        count.add(new JLabel("Games"));
        count.add(games);
        count.add(new JLabel("Grace (ms)"));
        count.add(grace);
        setupControls.add(games);
        setupControls.add(grace);
        p.add(count);

        JPanel openings = new JPanel(new GridBagLayout());
        openings.setBorder(BorderFactory.createTitledBorder("Openings (each is played twice, colours swapped)"));
        ButtonGroup group = new ButtonGroup();
        group.add(randomOpenings);
        group.add(fileOpenings);
        plies.setValue(prefs.plies());
        seed.setValue((int) prefs.seed());
        openingFile.setText(prefs.openingFile());
        (prefs.openingFile().isEmpty() ? randomOpenings : fileOpenings).setSelected(true);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        openings.add(randomOpenings, c);
        openings.add(plies, c);
        openings.add(new JLabel("seed"), c);
        openings.add(seed, c);
        c.gridy = 1;
        openings.add(fileOpenings, c);
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        openings.add(openingFile, c);
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        openings.add(browse, c);
        browse.addActionListener(e -> browseOpenings());
        randomOpenings.addActionListener(e -> enableSetup(controller == null || !isRunning()));
        fileOpenings.addActionListener(e -> enableSetup(controller == null || !isRunning()));
        setupControls.add(randomOpenings);
        setupControls.add(fileOpenings);
        p.add(openings);

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        options.add(live);
        live.addActionListener(e -> showLive = live.isSelected());
        p.add(options);

        JPanel run = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        run.add(start);
        run.add(pause);
        run.add(stop);
        start.addActionListener(e -> startMatch());
        pause.addActionListener(e -> pauseOrResume());
        stop.addActionListener(e -> {
            if (controller != null) {
                controller.stop();
            }
        });
        p.add(run);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        tools.add(editEngines);
        tools.add(console);
        tools.add(saveResults);
        saveResults.addActionListener(e -> saveResults());
        editEngines.addActionListener(e -> editEngines());
        console.addActionListener(e -> showConsole());
        p.add(tools);
        return p;
    }

    private JPanel engineBox(String title, JComboBox<String> combo, JTextArea extra, String extraText) {
        JPanel box = new JPanel(new BorderLayout(4, 4));
        box.setBorder(BorderFactory.createTitledBorder(title));
        extra.setText(extraText);
        extra.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        extra.setToolTipText("Options for this match only, one Name=value per line");
        JPanel top = new JPanel(new BorderLayout(4, 0));
        top.add(combo, BorderLayout.CENTER);
        box.add(top, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(extra);
        scroll.setBorder(BorderFactory.createTitledBorder("Extra options (this match only)"));
        box.add(scroll, BorderLayout.CENTER);
        setupControls.add(combo);
        setupControls.add(extra);
        return box;
    }

    private JPanel buildBoardArea() {
        JPanel area = new JPanel(new BorderLayout(0, 4));
        JPanel players = new JPanel(new GridLayout(1, 2, 8, 0));
        for (JLabel l : new JLabel[] {xPlayer, oPlayer}) {
            l.setOpaque(true);
            l.setBackground(IDLE_BACKGROUND);
            l.setFont(l.getFont().deriveFont(Font.BOLD, 14f));
            l.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            players.add(l);
        }
        board.setInputEnabled(false);
        area.add(players, BorderLayout.NORTH);
        area.add(board, BorderLayout.CENTER);
        gameStatus.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        area.add(gameStatus, BorderLayout.SOUTH);
        board.setPreferredSize(new Dimension(520, 520));
        return area;
    }

    private JPanel buildAnalysis() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(analysisA);
        p.add(analysisB);
        return p;
    }

    private JPanel buildBottom() {
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        JPanel score = new JPanel(new BorderLayout(8, 0));
        score.add(standingsLabel, BorderLayout.CENTER);
        progress.setStringPainted(true);
        progress.setPreferredSize(new Dimension(220, 20));
        JPanel progressBox = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        progressBox.add(progress);
        score.add(progressBox, BorderLayout.EAST);
        // Room for the five lines the standings can have, so the layout does not move as they appear.
        standingsLabel.setPreferredSize(new Dimension(300, 5 * standingsLabel.getFontMetrics(standingsLabel.getFont()).getHeight() + 6));
        standingsLabel.setVerticalAlignment(JLabel.TOP);
        bottom.add(score, BorderLayout.NORTH);
        JTable games = new JTable(table);
        games.setFillsViewportHeight(true);
        games.setToolTipText("Double-click a game to review it in the main window");
        games.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = games.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        reviewRow(row);
                    }
                }
            }
        });
        games.getColumnModel().getColumn(0).setPreferredWidth(40);
        games.getColumnModel().getColumn(5).setPreferredWidth(50);
        games.getColumnModel().getColumn(6).setPreferredWidth(30);
        JScrollPane scroll = new JScrollPane(games);
        scroll.setPreferredSize(new Dimension(400, 140));
        bottom.add(scroll, BorderLayout.CENTER);
        return bottom;
    }

    // ---- setup handling ----

    /** Shows the engines in the lists, choosing {@code a} and {@code b} if they exist (else the first and second engine). */
    private void refillEngines(String a, String b) {
        engineA.removeAllItems();
        engineB.removeAllItems();
        for (EngineConfig e : engines) {
            engineA.addItem(e.name());
            engineB.addItem(e.name());
        }
        select(engineA, a, 0);
        select(engineB, b, Math.min(1, engines.size() - 1));
    }

    private void select(JComboBox<String> combo, String name, int fallbackIndex) {
        if (name != null && engines.stream().anyMatch(e -> e.name().equals(name))) {
            combo.setSelectedItem(name);
        } else if (fallbackIndex >= 0 && fallbackIndex < combo.getItemCount()) {
            combo.setSelectedIndex(fallbackIndex);
        }
    }

    /** Replaces the list of engines (after they were edited elsewhere), keeping the choices that still exist. */
    public void setEngines(List<EngineConfig> newEngines) {
        String a = (String) engineA.getSelectedItem();
        String b = (String) engineB.getSelectedItem();
        this.engines = List.copyOf(newEngines);
        refillEngines(a, b);
    }

    private void enableSetup(boolean enabled) {
        for (Component c : setupControls) {
            c.setEnabled(enabled);
        }
        boolean file = fileOpenings.isSelected();
        plies.setEnabled(enabled && !file);
        seed.setEnabled(enabled && !file);
        openingFile.setEnabled(enabled && file);
        browse.setEnabled(enabled && file);
        editEngines.setEnabled(enabled);
    }

    private void browseOpenings() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a file of opening positions (one FEN per line)");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            openingFile.setText(f.getAbsolutePath());
        }
    }

    private void editEngines() {
        List<EngineConfig> edited = EnginesDialog.show(this, engines);
        if (edited != null) {
            saveEngines.accept(edited);
            setEngines(edited);
        }
    }

    private EngineConfig find(String name) {
        return engines.stream().filter(e -> e.name().equals(name)).findFirst().orElse(null);
    }

    private MatchPrefs currentPrefs() {
        return new MatchPrefs((String) engineA.getSelectedItem(), (String) engineB.getSelectedItem(), timeControl.timeControl(),
                ((Number) games.getValue()).intValue(), ((Number) plies.getValue()).intValue(),
                ((Number) seed.getValue()).longValue(), ((Number) grace.getValue()).longValue(), extraA.getText(),
                extraB.getText(), fileOpenings.isSelected() ? openingFile.getText().strip() : "");
    }

    // ---- running a match ----

    /** Builds the match from the setup and starts it; problems with the setup are shown and nothing starts. */
    void startMatch() {
        MatchPrefs prefs = currentPrefs();
        EngineConfig a = prefs.engineA() == null ? null : find(prefs.engineA());
        EngineConfig b = prefs.engineB() == null ? null : find(prefs.engineB());
        if (a == null || b == null) {
            problem("Choose both engines first (Edit engines… adds them).");
            return;
        }
        MatchSetup s;
        try {
            Map<String, String> ea = EngineConfig.parseOptions(prefs.extraA());
            Map<String, String> eb = EngineConfig.parseOptions(prefs.extraB());
            List<Position> openings;
            if (prefs.openingFile().isEmpty()) {
                openings = Openings.random(Openings.needed(prefs.games()), prefs.plies(), prefs.seed());
            } else {
                openings = Openings.parseFens(Files.readString(java.nio.file.Path.of(prefs.openingFile())));
            }
            s = new MatchSetup(a, ea, b, eb, prefs.timeControl(), prefs.games(), openings, prefs.graceMs());
        } catch (IOException e) {
            problem("Cannot read the opening file: " + e.getMessage());
            return;
        } catch (RuntimeException e) {
            problem(e.getMessage());
            return;
        }
        savePrefs.accept(prefs);
        setup = s;
        table.setRowCount(0);
        finishedGames.clear();
        analysisA.clear();
        analysisB.clear();
        analysisA.setEngine("A: " + s.nameA());
        analysisB.setEngine("B: " + s.nameB());
        infoA.set(null);
        infoB.set(null);
        frame.set(null);
        clockState.set(null);
        progress.setMaximum(s.games());
        progress.setValue(0);
        progress.setString("0 / " + s.games());
        showStandings(Standings.empty());
        gameStatus.setText("Starting the engines…");
        showLive = live.isSelected();
        pausing = false;
        controller = new MatchController(s, new Events());
        enableSetup(false);
        controller.start();
        updateButtons();
    }

    private void problem(String message) {
        problemSink.accept(message);
    }

    /** Where problems with the setup go; by default a message dialog (replaced in tests, which have no display). */
    private Consumer<String> problemSink = message ->
            JOptionPane.showMessageDialog(this, message, "Engine match", JOptionPane.WARNING_MESSAGE);

    void setProblemSink(Consumer<String> sink) {
        this.problemSink = sink;
    }

    private void pauseOrResume() {
        if (controller == null) {
            return;
        }
        if (controller.state() == MatchController.State.PAUSED || pausing) {
            pausing = false;
            controller.resume();
        } else {
            pausing = true;
            controller.pause();
        }
        updateButtons();
    }

    /** Opens the game in row {@code row} of the list in the main window. */
    void reviewRow(int row) {
        if (row >= 0 && row < finishedGames.size() && setup != null) {
            reviewHandler.accept(MatchReport.record(setup, finishedGames.get(row), LocalDate.now()));
        }
    }

    /** Asks for a file and saves the match there: the summary and every finished game. */
    private void saveResults() {
        if (finishedGames.isEmpty()) {
            problem("No game has finished yet.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Ataxx games (*.ataxx)", "ataxx"));
        chooser.setSelectedFile(new File("match-" + LocalDate.now() + ".ataxx"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = chooser.getSelectedFile();
        if (f.exists() && JOptionPane.showConfirmDialog(this, f.getName() + " exists. Replace it?", "Save results",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        String problem = saveResultsTo(f.toPath());
        if (problem != null) {
            problem(problem);
        }
    }

    /** Writes the match to {@code file}; returns an error message, or null on success. */
    String saveResultsTo(java.nio.file.Path file) {
        if (setup == null || finishedGames.isEmpty()) {
            return "No game has finished yet.";
        }
        String status = controller == null ? "not started" : switch (controller.state()) {
            case FINISHED -> "finished";
            case STOPPED -> "stopped";
            case FAILED -> "failed";
            default -> "running";
        };
        try {
            Files.writeString(file, MatchReport.toText(setup, List.copyOf(finishedGames), controller.standings(), status,
                    LocalDate.now()));
            return null;
        } catch (IOException e) {
            return "Could not save the results: " + e.getMessage();
        }
    }

    /** True while a match is running or paused. */
    public boolean isRunning() {
        if (controller == null) {
            return false;
        }
        MatchController.State s = controller.state();
        return s == MatchController.State.RUNNING || s == MatchController.State.PAUSED || s == MatchController.State.READY;
    }

    private void updateButtons() {
        boolean running = isRunning();
        start.setEnabled(!running);
        pause.setEnabled(running);
        stop.setEnabled(running);
        boolean paused = controller != null && controller.state() == MatchController.State.PAUSED;
        pause.setText(paused ? "Resume" : pausing ? "Cancel pause" : "Pause after game");
    }

    /** Stops a running match and waits a moment for its thread; call before the window closes. */
    public void close() {
        timer.stop();
        if (controller != null) {
            controller.stop();
            try {
                controller.await(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (consoleFrame != null) {
            consoleFrame.dispose();
        }
    }

    // ---- match events (on the match thread; each passes the work to the event dispatch thread) ----

    private final class Events implements MatchController.Listener {
        @Override
        public void stateChanged(MatchController.State state) {
            SwingUtilities.invokeLater(() -> {
                // The pause that was asked for has taken effect (or been ended): the button shows the real state.
                pausing = false;
                updateButtons();
            });
        }

        @Override
        public void gameStarted(int index, int total, Position opening, String x, String o) {
            aIsX = MatchSetup.aIsX(index);
            clockState.set(null);
            infoA.set(null);
            infoB.set(null);
            SwingUtilities.invokeLater(() -> {
                xName = x;
                oName = o;
                analysisA.clear();
                analysisB.clear();
                frame.set(new Frame(opening, null));
                gameStatus.setText("Game " + (index + 1) + " of " + total + ": " + x + " (x) vs " + o + " (o)");
                updatePlayers(opening.sideToMove(), null);
                board.setPosition(opening, null);
            });
        }

        @Override
        public void moveRequested(Game game, Side side, long xRemainingMs, long oRemainingMs) {
            if (showLive) {
                clockState.set(new ClockState(side, xRemainingMs, oRemainingMs, System.nanoTime()));
                frame.set(new Frame(game.position(), game.lastMove()));
            }
        }

        @Override
        public void info(Side side, UaiInfo info) {
            if (showLive) {
                ((side == Side.X) == aIsX ? infoA : infoB).set(info);
            }
        }

        @Override
        public void moved(Game game, Move move, Side mover, long nanos) {
            if (showLive) {
                frame.set(new Frame(game.position(), move));
            }
        }

        @Override
        public void gameFinished(MatchGame game, Standings standings) {
            SwingUtilities.invokeLater(() -> {
                Game g = game.result().game();
                frame.set(null);
                clockState.set(null);
                board.setPosition(g.position(), g.lastMove());
                updatePlayers(null, null);
                finishedGames.add(game);
                String result = GameRecord.resultTag(game.result().outcome());
                double a = game.scoreForA();
                table.addRow(new Object[] {game.index() + 1, game.aIsX() ? setup.nameA() : setup.nameB(),
                        game.aIsX() ? setup.nameB() : setup.nameA(), result, game.result().termination(), g.moves().size(),
                        a == 1.0 ? "+" : a == 0.5 ? "=" : "-"});
                progress.setValue(standings.games());
                progress.setString(standings.games() + " / " + setup.games());
                gameStatus.setText("Game " + (game.index() + 1) + ": " + result + " (" + game.result().termination() + ")");
                showStandings(standings);
            });
        }

        @Override
        public void line(char engine, String text) {
            synchronized (consoleHistory) {
                consoleHistory.add("[" + engine + "] " + text + "\n");
                consoleTotal++;
                while (consoleHistory.size() > EngineLogFrame.MAX_LINES) {
                    consoleHistory.removeFirst();
                }
            }
        }

        @Override
        public void matchFinished(MatchController.State finalState, Standings standings, String message) {
            SwingUtilities.invokeLater(() -> {
                frame.set(null);
                clockState.set(null);
                showStandings(standings);
                String what = switch (finalState) {
                    case FINISHED -> "Match finished";
                    case STOPPED -> "Match stopped";
                    default -> "Match failed";
                };
                gameStatus.setText(message.isEmpty() ? what : what + ": " + message);
                updatePlayers(null, null);
                pausing = false;
                enableSetup(true);
                updateButtons();
                if (finalState == MatchController.State.FAILED) {
                    problem(message);
                }
            });
        }
    }

    // ---- display ----

    private void showStandings(Standings s) {
        String a = setup == null ? "A" : setup.nameA();
        String b = setup == null ? "B" : setup.nameB();
        StringBuilder sb = new StringBuilder("<html>");
        for (String line : StandingsText.lines(a, b, s)) {
            sb.append(line.replace("&", "&amp;").replace("<", "&lt;")).append("<br>");
        }
        standingsLabel.setText(sb.append("</html>").toString());
    }

    /** Called about ten times a second: shows the latest board, analysis, clocks and console lines. */
    private void flush() {
        Frame f = frame.get();
        if (f != null && showLive) {
            board.setPosition(f.position(), f.lastMove());
        }
        if (showLive) {
            UaiInfo a = infoA.getAndSet(null);
            if (a != null) {
                analysisA.setInfo(a);
            }
            UaiInfo b = infoB.getAndSet(null);
            if (b != null) {
                analysisB.setInfo(b);
            }
        }
        if (f != null) {
            updatePlayers(f.position().isGameOver() ? null : f.position().sideToMove(), clockState.get());
        }
        flushConsole();
    }

    private void updatePlayers(Side toMove, ClockState clock) {
        xPlayer.setText(label("x", xName, Side.X, clock));
        oPlayer.setText(label("o", oName, Side.O, clock));
        xPlayer.setBackground(toMove == Side.X ? RUNNING_BACKGROUND : IDLE_BACKGROUND);
        oPlayer.setBackground(toMove == Side.O ? RUNNING_BACKGROUND : IDLE_BACKGROUND);
    }

    private String label(String letter, String name, Side side, ClockState clock) {
        String text = letter + "  " + name;
        if (clock != null && clock.xRemainingMs() >= 0) {
            long remaining = side == Side.X ? clock.xRemainingMs() : clock.oRemainingMs();
            if (clock.toMove() == side) {
                remaining -= (System.nanoTime() - clock.nanoStamp()) / 1_000_000L;
            }
            text += "   " + MainPanel.formatClock(remaining);
        }
        return text;
    }

    private void flushConsole() {
        if (consoleFrame == null || !consoleFrame.isShowing()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        synchronized (consoleHistory) {
            long fresh = consoleTotal - consoleShown;
            if (fresh <= 0) {
                return;
            }
            int skip = (int) Math.max(0, consoleHistory.size() - fresh);
            int i = 0;
            for (String line : consoleHistory) {
                if (i++ >= skip) {
                    sb.append(line);
                }
            }
            consoleShown = consoleTotal;
        }
        consoleFrame.appendText(sb.toString());
    }

    private void showConsole() {
        if (consoleFrame == null) {
            consoleFrame = new EngineLogFrame();
            consoleFrame.setTitle("uai-board: match console");
            StringBuilder sb = new StringBuilder();
            synchronized (consoleHistory) {
                consoleHistory.forEach(sb::append);
                consoleShown = consoleTotal;
            }
            consoleFrame.appendText(sb.toString());
        }
        consoleFrame.setVisible(true);
    }

    // ---- for tests ----

    void stopForTest() {
        stop.doClick();
    }

    MatchController.State matchState() {
        return controller == null ? null : controller.state();
    }

    int finishedRows() {
        return table.getRowCount();
    }

    String standingsText() {
        return standingsLabel.getText();
    }

    String statusText() {
        return gameStatus.getText();
    }

    boolean startEnabled() {
        return start.isEnabled();
    }

    boolean stopEnabled() {
        return stop.isEnabled();
    }

    Object tableValue(int row, int column) {
        return table.getValueAt(row, column);
    }
}
