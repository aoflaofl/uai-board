package spamalot.uaiboard.play;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import spamalot.uaiboard.game.Game;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Outcome;
import spamalot.uaiboard.game.Position;
import spamalot.uaiboard.game.Side;
import spamalot.uaiboard.uai.GoParams;
import spamalot.uaiboard.uai.UaiClient;
import spamalot.uaiboard.uai.UaiInfo;

/**
 * Runs a game: two people at one board, or a person against a UAI engine. It owns the {@link Game}, the engine session and the
 * clock, decides whose turn it is, asks the engine for its moves and ignores answers that belong to a game or position that has
 * since been replaced.
 *
 * <p>Its methods must be called from one thread, the one that {@code ui} runs on (the Swing event dispatch thread in the
 * application); every engine event is passed to that thread through {@code ui} before it touches any state or the {@link View}.
 */
public final class PlayController implements AutoCloseable {
    /** What the controller reports; every call is made on the {@code ui} thread. */
    public interface View {
        /** The position changed: a new game, a move or an undo. Ask the controller whether input is allowed. */
        void positionChanged();

        /** A short status for the engine, such as "starting" or "thinking"; empty when there is nothing to say. */
        void engineStatus(String text);

        /** The engine's latest analysis of the position it is searching. */
        void info(UaiInfo info);

        /** The game ended, by the rules, by a flag, or because a side broke the rules. */
        void gameOver(Outcome outcome, String reason);

        /** The engine failed. The game goes on as a game between two people. */
        void problem(String message);

        /** A line to or from the engine, for a console. */
        void engineLine(UaiClient.Direction direction, String line);
    }

    /** Extra time allowed beyond the move time before an engine that has not answered is given up on. */
    static final long DEFAULT_ANSWER_GRACE_MS = 5000;

    private final Executor ui;
    private final View view;
    private final LongSupplier nanoTime;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(daemonThreads("uai-launch"));
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(daemonThreads("uai-watchdog"));

    private Game game = new Game();
    private GameSetup setup = GameSetup.DEFAULT;
    private EngineConfig engineConfig;
    private EngineSession session;
    private boolean engineReady;
    private boolean engineGone;
    private boolean thinking;
    private GameClock clock;
    private long gameGeneration;
    private long searchSerial;
    private long answerGraceMs = DEFAULT_ANSWER_GRACE_MS;
    private Outcome forcedOutcome;
    private String forcedReason = "";

    public PlayController(Executor ui, View view) {
        this(ui, view, System::nanoTime);
    }

    public PlayController(Executor ui, View view, LongSupplier nanoTime) {
        this.ui = ui;
        this.view = view;
        this.nanoTime = nanoTime;
    }

    private static java.util.concurrent.ThreadFactory daemonThreads(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    /** How long beyond its time an engine may take to answer before it is given up on (for tests). */
    void setAnswerGraceMs(long ms) {
        this.answerGraceMs = ms;
    }

    public Game game() {
        return game;
    }

    public GameSetup setup() {
        return setup;
    }

    private boolean usesEngine() {
        return engineConfig != null && !engineGone;
    }

    /** True when the game is over, by the rules or otherwise. */
    public boolean isOver() {
        return forcedOutcome != null || game.position().isGameOver();
    }

    /** True when a click on the board should be accepted: the game is on and it is a person's turn. */
    public boolean inputAllowed() {
        if (isOver()) {
            return false;
        }
        return !usesEngine() || (engineReady && game.position().sideToMove() == setup.humanSide());
    }

    /** Milliseconds left for {@code side} on the game clock, or -1 if there is no clock. */
    public long clockMillis(Side side) {
        return clock == null ? -1 : clock.remaining(side);
    }

    /** The side whose clock is running, or null. */
    public Side clockRunning() {
        return clock == null ? null : clock.running();
    }

    /** The outcome of the game, including a loss on time or by a broken rule. */
    public Outcome outcome() {
        return forcedOutcome != null ? forcedOutcome : game.position().outcome();
    }

    /** Starts a new game from the standard start position; see {@link #startGame(GameSetup, EngineConfig, Position)}. */
    public void startGame(GameSetup setup, EngineConfig engine) {
        startGame(setup, engine, Position.start());
    }

    /**
     * Starts a new game from {@code start}. With an engine, the process is started in the background and play begins once it is
     * ready. Any game and engine in progress are dropped.
     */
    public void startGame(GameSetup setup, EngineConfig engine, Position start) {
        begin(setup, setup.vsEngine() ? engine : null, new Game(start));
    }

    /**
     * Continues a loaded game as a game between two people (the engine, if any, is dropped); {@code loaded} is played on from
     * its last position.
     */
    public void loadGame(Game loaded) {
        begin(new GameSetup(null, Side.X, GameSetup.DEFAULT.timeControl()), null, loaded);
    }

    private void begin(GameSetup setup, EngineConfig engine, Game newGame) {
        gameGeneration++;
        searchSerial++;
        long gen = gameGeneration;
        dropEngine();
        this.setup = setup;
        this.engineConfig = engine;
        this.engineGone = false;
        this.engineReady = false;
        this.thinking = false;
        this.forcedOutcome = null;
        this.forcedReason = "";
        this.game = newGame;
        this.clock = null;
        if (engineConfig != null && setup.timeControl() instanceof TimeControl.Clock c) {
            clock = new GameClock(c.baseMillis(), c.incrementMillis(), nanoTime);
        }
        afterMove();
        if (engineConfig == null) {
            view.engineStatus("");
            return;
        }
        view.engineStatus("starting " + engineConfig.name() + "…");
        EngineConfig config = engineConfig;
        worker.execute(() -> launch(config, gen));
    }

    private void launch(EngineConfig config, long gen) {
        EngineSession started;
        try {
            started = EngineSession.launch(config, callbacks(gen));
        } catch (Exception e) {
            ui.execute(() -> {
                if (gen == gameGeneration) {
                    engineFailed(e.getMessage());
                }
            });
            return;
        }
        EngineSession s = started;
        ui.execute(() -> {
            if (gen != gameGeneration) {
                s.close();
                return;
            }
            session = s;
            engineReady = true;
            view.engineStatus(s.engineName());
            if (clock != null) {
                clock.start(game.position().sideToMove());
            }
            view.positionChanged();
            engineToMoveIfDue();
        });
    }

    private EngineSession.Callbacks callbacks(long gen) {
        return new EngineSession.Callbacks() {
            @Override
            public void onInfo(UaiInfo info) {
                ui.execute(() -> {
                    if (gen == gameGeneration && thinking) {
                        view.info(info);
                    }
                });
            }

            @Override
            public void onBestMove(Object token, Move move) {
                ui.execute(() -> engineAnswered(gen, token, move));
            }

            @Override
            public void onProblem(String message) {
                ui.execute(() -> {
                    if (gen == gameGeneration) {
                        engineFailed(message);
                    }
                });
            }

            @Override
            public void onLine(UaiClient.Direction direction, String line) {
                ui.execute(() -> {
                    if (gen == gameGeneration) {
                        view.engineLine(direction, line);
                    }
                });
            }
        };
    }

    /** A person moves. Ignored unless {@link #inputAllowed()}. */
    public void humanMove(Move move) {
        if (!inputAllowed() || !game.position().isLegal(move)) {
            return;
        }
        play(move);
    }

    private void play(Move move) {
        Side mover = game.position().sideToMove();
        game.play(move);
        if (clock != null && clock.running() != null) {
            clock.switchTo(mover, game.position().sideToMove());
        }
        afterMove();
    }

    private void afterMove() {
        // A side that cannot move passes on its own.
        while (!game.position().isGameOver() && game.position().legalMoves().equals(List.of(Move.pass()))) {
            Side mover = game.position().sideToMove();
            game.play(Move.pass());
            if (clock != null && clock.running() != null) {
                clock.switchTo(mover, game.position().sideToMove());
            }
        }
        view.positionChanged();
        if (game.position().isGameOver()) {
            finish(game.position().outcome(), "");
            return;
        }
        engineToMoveIfDue();
    }

    private void engineToMoveIfDue() {
        if (!usesEngine() || !engineReady || isOver() || thinking) {
            return;
        }
        if (game.position().sideToMove() == setup.humanSide()) {
            return;
        }
        Position p = game.position();
        GoParams go;
        long answerWithin;
        if (setup.timeControl() instanceof TimeControl.MoveTime t) {
            go = GoParams.movetime(t.millis());
            answerWithin = t.millis() + answerGraceMs;
        } else {
            long x = Math.max(1, clock.remaining(Side.X));
            long o = Math.max(1, clock.remaining(Side.O));
            go = GoParams.clock(x, o, clock.incrementMs(), clock.incrementMs());
            answerWithin = Math.max(x, o) + answerGraceMs;
        }
        thinking = true;
        view.engineStatus(session.engineName() + " is thinking…");
        long serial = ++searchSerial;
        session.requestMove(serial, game.start(), game.moves(), go);
        watchdog.schedule(() -> ui.execute(() -> {
            if (thinking && serial == searchSerial) {
                engineFailed("the engine did not answer in time");
            }
        }), answerWithin, TimeUnit.MILLISECONDS);
    }

    private void engineAnswered(long gen, Object token, Move move) {
        if (gen != gameGeneration || !thinking || (Long) token != searchSerial) {
            return;
        }
        thinking = false;
        view.engineStatus(session.engineName());
        if (isOver()) {
            return;
        }
        if (!game.position().isLegal(move)) {
            Side engineSide = setup.humanSide().opponent();
            finish(engineSide == Side.X ? Outcome.O_WINS : Outcome.X_WINS,
                    session.engineName() + " played an illegal move (" + move + ")");
            return;
        }
        play(move);
    }

    private void engineFailed(String message) {
        if (engineGone) {
            return;
        }
        engineGone = true;
        thinking = false;
        engineReady = false;
        dropEngine();
        if (clock != null) {
            clock.stop();
            clock = null;
        }
        view.engineStatus("");
        view.problem(message);
        view.positionChanged();
    }

    /** The side that would resign: the person's side against an engine, otherwise the side to move. */
    private Side resigningSide() {
        return usesEngine() ? setup.humanSide() : game.position().sideToMove();
    }

    public boolean canResign() {
        return !isOver() && (!usesEngine() || engineReady);
    }

    /** The person (or, between two people, the side to move) resigns and the other side wins. */
    public void resign() {
        if (!canResign()) {
            return;
        }
        Side loser = resigningSide();
        finish(loser == Side.X ? Outcome.O_WINS : Outcome.X_WINS, (loser == Side.X ? "x" : "o") + " resigned");
    }

    /** Draws by agreement are only possible between two people: an engine cannot be offered a draw over UAI. */
    public boolean canAgreeDraw() {
        return !isOver() && !usesEngine();
    }

    public void agreeDraw() {
        if (canAgreeDraw()) {
            finish(Outcome.DRAW, "draw agreed");
        }
    }

    /** The engine's name (as it gave it, or as configured while it starts), or null in a game between two people. */
    public String opponentName() {
        if (!usesEngine()) {
            return null;
        }
        return session != null ? session.engineName() : engineConfig.name();
    }

    /** Called regularly (a few times a second) to notice a fallen flag. */
    public void tick() {
        if (clock == null || isOver()) {
            return;
        }
        Side flagged = clock.flagged();
        if (flagged != null) {
            if (session != null) {
                session.cancel();
            }
            thinking = false;
            finish(flagged == Side.X ? Outcome.O_WINS : Outcome.X_WINS,
                    (flagged == Side.X ? "x" : "o") + " lost on time");
        }
    }

    private void finish(Outcome outcome, String reason) {
        if (clock != null) {
            clock.stop();
        }
        if (game.position().isGameOver()) {
            forcedOutcome = null;
        } else {
            forcedOutcome = outcome;
            forcedReason = reason;
        }
        searchSerial++;
        if (session != null) {
            session.cancel();
        }
        thinking = false;
        view.positionChanged();
        view.gameOver(outcome, reason);
    }

    /**
     * Takes back moves. Against an engine it goes back to the last position where it was the person's turn (undoing the
     * engine's reply as well); between two people it takes back one move. Passes are taken back with the move before them.
     */
    public void undo() {
        if (game.moves().isEmpty() || (clock != null && (clock.remaining(Side.X) <= 0 || clock.remaining(Side.O) <= 0))) {
            return;
        }
        searchSerial++;
        if (session != null) {
            session.cancel();
        }
        thinking = false;
        forcedOutcome = null;
        forcedReason = "";
        popMove();
        if (usesEngine()) {
            while (!game.moves().isEmpty() && game.position().sideToMove() != setup.humanSide()) {
                popMove();
            }
        }
        if (clock != null) {
            clock.resume(game.position().sideToMove());
        }
        // A cancelled search answers once at most and is ignored; the next request waits for that answer.
        view.positionChanged();
        if (usesEngine()) {
            view.engineStatus(session == null ? "" : session.engineName());
        }
        engineToMoveIfDue();
    }

    private void popMove() {
        game.undo();
        while (game.lastMove() != null && game.lastMove().isPass()) {
            game.undo();
        }
    }

    private void dropEngine() {
        if (session != null) {
            EngineSession s = session;
            session = null;
            Analyser.closeInBackground(s);
        }
        engineReady = false;
    }

    @Override
    public void close() {
        gameGeneration++;
        searchSerial++;
        engineReady = false;
        EngineSession s = session;
        session = null;
        worker.shutdown();
        watchdog.shutdownNow();
        if (s != null) {
            s.close();
        }
    }
}
