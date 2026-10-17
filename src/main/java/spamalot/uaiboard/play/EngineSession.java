package spamalot.uaiboard.play;

import java.time.Duration;
import java.util.List;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Position;
import spamalot.uaiboard.uai.GoParams;
import spamalot.uaiboard.uai.UaiClient;
import spamalot.uaiboard.uai.UaiException;
import spamalot.uaiboard.uai.UaiInfo;

/**
 * One running engine, used to play one game: it starts the process, sets the configured options, and runs one search at a time.
 * A search that is cancelled never reports its move, and a request made while another search is still ending waits until the
 * engine has answered the old one, so a late {@code bestmove} can never be mistaken for the answer to a newer position.
 *
 * <p>Callbacks run on the client's reader thread.
 */
public final class EngineSession implements AutoCloseable {
    /** What the session reports. */
    public interface Callbacks {
        default void onInfo(UaiInfo info) { }

        /**
         * An {@code info} line of the search requested with {@code token} (null for lines the engine printed while starting up).
         * Lines of a search that has been cancelled are never passed on. The default hands the line to {@link #onInfo(UaiInfo)}.
         */
        default void onInfo(Object token, UaiInfo info) {
            onInfo(info);
        }

        /** The engine's answer to the request made with {@code token}. */
        default void onBestMove(Object token, Move move) { }

        /** The engine failed (exited, wrote an unreadable move, or could not be written to); the session is unusable. */
        default void onProblem(String message) { }

        default void onLine(UaiClient.Direction direction, String line) { }
    }

    private enum State { IDLE, THINKING, CANCELLING }

    private record Request(Object token, Position start, List<Move> moves, GoParams go) { }

    private final UaiClient client;
    private final Callbacks callbacks;
    private final String engineName;
    private State state = State.IDLE;
    private Object activeToken;
    private Request queued;
    private boolean closed;
    private boolean failed;

    private EngineSession(UaiClient client, Callbacks callbacks, String engineName) {
        this.client = client;
        this.callbacks = callbacks;
        this.engineName = engineName;
    }

    /**
     * Starts the engine, shakes hands, sets the options and tells it a new game begins. Blocks, so call it from a worker thread.
     *
     * @throws UaiException if the engine cannot be started or does not answer
     */
    public static EngineSession launch(EngineConfig config, Callbacks callbacks) throws UaiException {
        EngineSession[] holder = new EngineSession[1];
        UaiClient.Listener listener = new UaiClient.Listener() {
            @Override
            public void onInfo(UaiInfo info) {
                EngineSession s = holder[0];
                if (s == null) {
                    callbacks.onInfo(null, info);
                    return;
                }
                Object token;
                synchronized (s) {
                    if (s.state != State.THINKING) {
                        return; // a cancelled search may still print for a moment
                    }
                    token = s.activeToken;
                }
                callbacks.onInfo(token, info);
            }

            @Override
            public void onBestMove(Move move) {
                if (holder[0] != null) {
                    holder[0].bestMove(move);
                }
            }

            @Override
            public void onLine(UaiClient.Direction direction, String line) {
                callbacks.onLine(direction, line);
            }

            @Override
            public void onProtocolError(String message) {
                if (holder[0] != null) {
                    holder[0].fail(message);
                }
            }

            @Override
            public void onExit(int exitCode) {
                if (holder[0] != null) {
                    holder[0].exited(exitCode);
                }
            }
        };
        List<String> command;
        try {
            command = config.commandLine();
        } catch (IllegalArgumentException e) {
            throw new UaiException("bad engine command: " + e.getMessage(), e);
        }
        UaiClient client = UaiClient.start(command, listener);
        try {
            UaiClient.EngineId id = client.handshake(Duration.ofSeconds(15));
            for (var option : config.options().entrySet()) {
                client.setOption(option.getKey(), option.getValue());
            }
            client.newGame();
            String name = id.name().isEmpty() ? config.name() : id.name();
            EngineSession session = new EngineSession(client, callbacks, name);
            holder[0] = session;
            return session;
        } catch (UaiException | RuntimeException e) {
            client.close();
            throw e instanceof UaiException u ? u : new UaiException(e.getMessage(), e);
        }
    }

    /**
     * Tells the engine a new game begins and waits until it is ready. Blocks, so call it from a worker thread, and only while no
     * search is running.
     *
     * @throws UaiException if a search is still running or the engine does not answer
     */
    public void newGame() throws UaiException {
        if (isSearching()) {
            throw new UaiException("the engine is still searching");
        }
        client.newGame();
    }

    /** True while a search is running or being stopped. */
    public synchronized boolean isSearching() {
        return state != State.IDLE;
    }

    /** True while a stopped search has not yet been answered: an engine that stays in this state ignores {@code stop}. */
    public synchronized boolean isCancelling() {
        return state == State.CANCELLING;
    }

    /** The name the engine gave itself in the handshake. */
    public String engineName() {
        return engineName;
    }

    public boolean isAlive() {
        return !failed && client.isAlive();
    }

    /**
     * Asks for a move in {@code start} followed by {@code moves}. If a search is running it is stopped first; the new search
     * begins once the engine has answered the old one.
     */
    public void requestMove(Object token, Position start, List<Move> moves, GoParams go) {
        String problem = null;
        synchronized (this) {
            Request r = new Request(token, start, List.copyOf(moves), go);
            if (state == State.IDLE) {
                problem = begin(r);
            } else {
                queued = r;
                if (state == State.THINKING) {
                    state = State.CANCELLING;
                    problem = stopQuietly();
                }
            }
        }
        report(problem);
    }

    /** Abandons the current search, if any; its move will not be reported. */
    public void cancel() {
        String problem = null;
        synchronized (this) {
            queued = null;
            if (state == State.THINKING) {
                state = State.CANCELLING;
                problem = stopQuietly();
            }
        }
        report(problem);
    }

    private String begin(Request r) {
        try {
            state = State.THINKING;
            activeToken = r.token();
            client.setPosition(r.start(), r.moves());
            client.go(r.go());
            return null;
        } catch (UaiException e) {
            return e.getMessage();
        }
    }

    private String stopQuietly() {
        try {
            client.stop();
            return null;
        } catch (UaiException e) {
            return e.getMessage();
        }
    }

    private void bestMove(Move move) {
        Object token = null;
        boolean report = false;
        String problem = null;
        synchronized (this) {
            if (state == State.THINKING) {
                token = activeToken;
                activeToken = null;
                state = State.IDLE;
                report = true;
            } else if (state == State.CANCELLING) {
                state = State.IDLE;
                activeToken = null;
                if (queued != null) {
                    Request r = queued;
                    queued = null;
                    problem = begin(r);
                }
            }
        }
        if (report) {
            callbacks.onBestMove(token, move);
        }
        report(problem);
    }

    private void exited(int code) {
        boolean expected;
        synchronized (this) {
            expected = closed;
        }
        if (!expected) {
            fail("the engine exited (exit code " + code + ")");
        }
    }

    private void fail(String message) {
        synchronized (this) {
            if (failed || closed) {
                return;
            }
            failed = true;
        }
        callbacks.onProblem(message);
    }

    private void report(String problem) {
        if (problem != null) {
            fail(problem);
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            closed = true;
        }
        client.close();
    }
}
