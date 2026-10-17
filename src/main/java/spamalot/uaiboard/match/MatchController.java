package spamalot.uaiboard.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import spamalot.uaiboard.game.Game;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Position;
import spamalot.uaiboard.game.Side;
import spamalot.uaiboard.play.EngineConfig;
import spamalot.uaiboard.uai.UaiException;
import spamalot.uaiboard.uai.UaiInfo;

/**
 * Runs a match on a thread of its own: it starts the two engines, plays the games one after the other (each opening twice with
 * the colours swapped), keeps the score, and restarts an engine that failed before the next game.
 *
 * <p>All {@link Listener} methods are called on the match thread (the {@code info} method on an engine client's reader thread), so
 * a user interface must pass them on to its own thread. The controller can be paused (the game in progress is finished first) and
 * stopped (the game in progress is abandoned and not counted).
 */
public final class MatchController {
    /** Where a match is. */
    public enum State {
        READY, RUNNING, PAUSED, FINISHED, STOPPED, FAILED
    }

    /** Creates an engine for a match; replaceable in tests. */
    @FunctionalInterface
    interface PlayerFactory {
        EnginePlayer launch(EngineConfig config, Map<String, String> extra, Consumer<String> lines)
                throws UaiException;
    }

    /** What the match reports; every method has an empty default. */
    public interface Listener {
        default void stateChanged(State state) { }

        default void gameStarted(int index, int total, Position opening, String xName, String oName) { }

        /** An engine is about to be asked for a move; remaining times in ms, or -1 without a game clock. */
        default void moveRequested(Game game, Side side, long xRemainingMs, long oRemainingMs) { }

        default void info(Side side, UaiInfo info) { }

        default void moved(Game game, Move move, Side mover, long nanos) { }

        default void gameFinished(MatchGame game, Standings standings) { }

        /** A line to or from an engine; {@code engine} is {@code 'A'} or {@code 'B'}. */
        default void line(char engine, String line) { }

        /** The match is over; {@code message} explains a failure (empty otherwise). */
        default void matchFinished(State finalState, Standings standings, String message) { }
    }

    private final MatchSetup setup;
    private final Listener listener;
    private final PlayerFactory factory;
    private final Object lock = new Object();
    private final AtomicBoolean cancel = new AtomicBoolean();
    private final List<MatchGame> games = new ArrayList<>();
    private Standings standings = Standings.empty();
    private State state = State.READY;
    private boolean pauseRequested;
    private boolean stopRequested;
    private Thread thread;
    private EnginePlayer a;
    private EnginePlayer b;

    public MatchController(MatchSetup setup, Listener listener) {
        this(setup, listener, EnginePlayer::launch);
    }

    MatchController(MatchSetup setup, Listener listener, PlayerFactory factory) {
        this.setup = setup;
        this.listener = listener;
        this.factory = factory;
    }

    public MatchSetup setup() {
        return setup;
    }

    /** Starts the match on its own thread; it can be started once. */
    public synchronized void start() {
        if (thread != null) {
            throw new IllegalStateException("the match has been started already");
        }
        thread = new Thread(this::run, "uai-match");
        thread.setDaemon(true);
        thread.start();
    }

    /** Pauses before the next game starts (the game in progress is played out). */
    public void pause() {
        synchronized (lock) {
            pauseRequested = true;
        }
    }

    public void resume() {
        synchronized (lock) {
            pauseRequested = false;
            lock.notifyAll();
        }
    }

    /** Ends the match now: the game in progress is abandoned and not counted. */
    public void stop() {
        synchronized (lock) {
            stopRequested = true;
            cancel.set(true);
            lock.notifyAll();
        }
    }

    public State state() {
        synchronized (lock) {
            return state;
        }
    }

    public Standings standings() {
        synchronized (lock) {
            return standings;
        }
    }

    /** The finished games so far, in order. */
    public List<MatchGame> games() {
        synchronized (lock) {
            return List.copyOf(games);
        }
    }

    /** Waits for the match thread to end; returns false if it is still running after {@code millis}. */
    public boolean await(long millis) throws InterruptedException {
        Thread t;
        synchronized (this) {
            t = thread;
        }
        if (t == null) {
            return true;
        }
        t.join(millis);
        return !t.isAlive();
    }

    private void setState(State s) {
        synchronized (lock) {
            state = s;
        }
        listener.stateChanged(s);
    }

    private boolean stopping() {
        synchronized (lock) {
            return stopRequested;
        }
    }

    private void run() {
        State end = State.FINISHED;
        String message = "";
        try {
            setState(State.RUNNING);
            for (int i = 0; i < setup.games(); i++) {
                if (!waitWhilePaused() || stopping()) {
                    end = State.STOPPED;
                    break;
                }
                prepareEngines();
                boolean aIsX = MatchSetup.aIsX(i);
                EnginePlayer x = aIsX ? a : b;
                EnginePlayer o = aIsX ? b : a;
                Position opening = setup.openingFor(i);
                listener.gameStarted(i, setup.games(), opening, aIsX ? setup.nameA() : setup.nameB(),
                        aIsX ? setup.nameB() : setup.nameA());
                GameResult r = GameDriver.play(opening, x, o, setup.timeControl(), setup.graceMs(), new GameDriver.Observer() {
                    @Override
                    public void moveRequested(Game game, Side side, long xRemainingMs, long oRemainingMs) {
                        listener.moveRequested(game, side, xRemainingMs, oRemainingMs);
                    }

                    @Override
                    public void info(Side side, UaiInfo info) {
                        listener.info(side, info);
                    }

                    @Override
                    public void moved(Game game, Move move, Side mover, long nanos) {
                        listener.moved(game, move, mover, nanos);
                    }
                }, cancel);
                if (!r.finished()) {
                    end = State.STOPPED;
                    break;
                }
                MatchGame game = new MatchGame(i, aIsX, r);
                Standings now;
                synchronized (lock) {
                    games.add(game);
                    standings = standings.with(game);
                    now = standings;
                }
                listener.gameFinished(game, now);
            }
            if (stopping()) {
                end = State.STOPPED;
            }
        } catch (UaiException e) {
            end = State.FAILED;
            message = e.getMessage();
        } catch (RuntimeException e) {
            end = State.FAILED;
            message = "unexpected error: " + e;
        } finally {
            closeEngines();
            setState(end);
            listener.matchFinished(end, standings(), message);
        }
    }

    /** Waits while paused; returns false if the match was stopped meanwhile. */
    private boolean waitWhilePaused() {
        synchronized (lock) {
            if (!pauseRequested || stopRequested) {
                return !stopRequested;
            }
        }
        setState(State.PAUSED);
        synchronized (lock) {
            while (pauseRequested && !stopRequested) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            if (stopRequested) {
                return false;
            }
        }
        setState(State.RUNNING);
        return true;
    }

    /** Starts an engine that is not running or has failed, or tells a healthy one that a new game begins. */
    private void prepareEngines() throws UaiException {
        a = prepare(a, setup.engineA(), setup.extraA(), setup.nameA(), 'A');
        b = prepare(b, setup.engineB(), setup.extraB(), setup.nameB(), 'B');
    }

    private EnginePlayer prepare(EnginePlayer current, EngineConfig config, Map<String, String> extra, String name, char label)
            throws UaiException {
        if (current != null && current.isHealthy()) {
            try {
                current.newGame();
                return current;
            } catch (UaiException e) {
                // fall through and start it again
            }
        }
        if (current != null) {
            current.close();
        }
        try {
            return factory.launch(config, extra, line -> listener.line(label, line));
        } catch (UaiException e) {
            throw new UaiException("cannot start " + name + ": " + e.getMessage(), e);
        }
    }

    private void closeEngines() {
        for (EnginePlayer p : new EnginePlayer[] {a, b}) {
            if (p != null) {
                p.close();
            }
        }
        a = null;
        b = null;
    }
}
