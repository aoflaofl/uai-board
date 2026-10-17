package spamalot.uaiboard.play;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Position;
import spamalot.uaiboard.game.Side;
import spamalot.uaiboard.uai.GoParams;
import spamalot.uaiboard.uai.UaiClient;
import spamalot.uaiboard.uai.UaiInfo;

/**
 * Analysis mode: an engine searches the position on the board without limit and reports what it finds, and starts again
 * whenever the position changes. It runs in its own engine process, so it can be used while playing against another engine.
 *
 * <p>Some engines ignore {@code stop} while they analyse (they keep searching and never send a {@code bestmove}). The analyser
 * therefore waits a moment for the old search to end and, if it does not, restarts the engine process; once it has seen an engine
 * do that it restarts the engine at once for every new position.
 *
 * <p>Like {@link PlayController}, its methods must be called from the one thread that {@code ui} runs on, and every engine event
 * reaches the {@link Listener} on that thread.
 */
public final class Analyser implements AutoCloseable {
    /** What the analyser reports; every call is made on the {@code ui} thread. */
    public interface Listener {
        /** A short status: starting the engine, analysing, or nothing to analyse. */
        void status(String text);

        /** An {@code info} line of the current search; {@code sideToMove} is the side to move in the position searched. */
        void info(UaiInfo info, Side sideToMove);

        /** The engine failed to start or died; analysis is off. */
        void problem(String message);

        /** A line to or from the engine, for a console. */
        void line(UaiClient.Direction direction, String line);
    }

    private final Executor ui;
    private final Listener listener;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "uai-analysis-launch");
        t.setDaemon(true);
        return t;
    });

    /** How long a stopped search may take to end before the engine is judged to ignore {@code stop}. */
    static final long STOP_PATIENCE_MS = 400;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "uai-analysis-watchdog");
        t.setDaemon(true);
        return t;
    });

    private long generation;
    private EngineConfig config;
    private boolean ignoresStop;
    private long serial;
    private EngineSession session;
    private boolean ready;
    private boolean running;
    private Position target;
    private List<Move> targetMoves = List.of();

    public Analyser(Executor ui, Listener listener) {
        this.ui = ui;
        this.listener = listener;
    }

    /** True while an engine is running or starting for analysis. */
    public boolean isRunning() {
        return running;
    }

    /** Starts analysing with {@code engine} (replacing any engine already in use); the engine starts in the background. */
    public void start(EngineConfig engine) {
        ignoresStop = false;
        launch(engine);
    }

    private void launch(EngineConfig engine) {
        config = engine;
        stopEngine();
        running = true;
        generation++;
        long gen = generation;
        listener.status("starting " + engine.name() + "…");
        worker.execute(() -> {
            EngineSession started;
            try {
                started = EngineSession.launch(engine, callbacks(gen));
            } catch (Exception e) {
                ui.execute(() -> {
                    if (gen == generation) {
                        failed(e.getMessage());
                    }
                });
                return;
            }
            EngineSession s = started;
            ui.execute(() -> {
                if (gen != generation) {
                    s.close();
                    return;
                }
                session = s;
                ready = true;
                search();
            });
        });
    }

    /** Stops analysing and shuts the engine down. */
    public void stop() {
        stopEngine();
        running = false;
        generation++;
        listener.status("");
    }

    /** The position to analyse is {@code start} with {@code moves} played; the search restarts if the engine is running. */
    public void analyse(Position start, List<Move> moves) {
        this.target = start;
        this.targetMoves = List.copyOf(moves);
        if (running && ready) {
            search();
        }
    }

    private Position current() {
        Position p = target;
        for (Move m : targetMoves) {
            p = p.makeMove(m);
        }
        return p;
    }

    private void search() {
        if (target == null || session == null) {
            return;
        }
        serial++;
        Position p = current();
        if (p.isGameOver()) {
            session.cancel();
            listener.status("the game is over: nothing to analyse");
            return;
        }
        listener.status(session.engineName() + " is analysing");
        boolean busy = session.isSearching();
        if (busy && ignoresStop) {
            launch(config); // the engine would not stop; a fresh process analyses the new position once it is ready
            return;
        }
        long mine = serial;
        long gen = generation;
        session.requestMove(mine, target, targetMoves, GoParams.infinite());
        if (busy) {
            scheduler.schedule(() -> ui.execute(() -> stopHonoured(mine, gen)), STOP_PATIENCE_MS, TimeUnit.MILLISECONDS);
        }
    }

    /** Called a moment after a search was stopped for a new position: restarts an engine that has not stopped. */
    private void stopHonoured(long mine, long gen) {
        if (gen == generation && mine == serial && running && session != null && session.isCancelling()) {
            ignoresStop = true;
            launch(config);
        }
    }

    private EngineSession.Callbacks callbacks(long gen) {
        return new EngineSession.Callbacks() {
            @Override
            public void onInfo(Object token, UaiInfo info) {
                if (token == null) {
                    return;
                }
                ui.execute(() -> {
                    if (gen == generation && ready && token.equals(serial) && target != null) {
                        listener.info(info, current().sideToMove());
                    }
                });
            }

            @Override
            public void onProblem(String message) {
                ui.execute(() -> {
                    if (gen == generation) {
                        failed(message);
                    }
                });
            }

            @Override
            public void onLine(UaiClient.Direction direction, String line) {
                ui.execute(() -> {
                    if (gen == generation) {
                        listener.line(direction, line);
                    }
                });
            }
        };
    }

    private void failed(String message) {
        stopEngine();
        running = false;
        generation++;
        listener.status("");
        listener.problem(message);
    }

    private void stopEngine() {
        ready = false;
        if (session != null) {
            EngineSession s = session;
            session = null;
            closeInBackground(s);
        }
    }

    /** Shutting an engine down can take a moment (an engine that ignores {@code quit} is killed after half a second). */
    static void closeInBackground(EngineSession s) {
        Thread t = new Thread(s::close, "uai-engine-close");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void close() {
        generation++;
        ready = false;
        running = false;
        EngineSession s = session;
        session = null;
        worker.shutdown();
        scheduler.shutdownNow();
        if (s != null) {
            s.close();
        }
    }
}
