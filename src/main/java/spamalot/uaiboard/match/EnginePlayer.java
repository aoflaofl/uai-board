package spamalot.uaiboard.match;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Position;
import spamalot.uaiboard.play.EngineConfig;
import spamalot.uaiboard.play.EngineSession;
import spamalot.uaiboard.uai.GoParams;
import spamalot.uaiboard.uai.UaiClient;
import spamalot.uaiboard.uai.UaiException;
import spamalot.uaiboard.uai.UaiInfo;

/**
 * An engine taking part in a match: it answers one move request at a time and blocks until the answer is there, which is what a
 * match thread wants. The time an answer took is measured when the engine's line is read, on the client's reader thread, so
 * it does not include any delay in the match thread or the user interface.
 */
public final class EnginePlayer implements AutoCloseable {
    /** An engine's move and how long it took to arrive, in nanoseconds from the request being sent. */
    public record Answer(Move move, long nanos) { }

    /** Why a request ended without an answer. */
    public enum Failure {
        /** The engine gave no answer in the time allowed. */
        NO_ANSWER,
        /** The engine exited or wrote something unreadable. */
        ENGINE_FAILED,
        /** The caller asked to stop waiting. */
        CANCELLED
    }

    /** The reason a request failed, with a message for the log. */
    public static final class FailedException extends Exception {
        private final Failure failure;

        FailedException(Failure failure, String message) {
            super(message);
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }

    private sealed interface Event permits Arrived, Problem { }

    private record Arrived(long token, Move move, long nanos) implements Event { }

    private record Problem(String message) implements Event { }

    private final String name;
    private final EngineSession session;
    private final BlockingQueue<Event> events;
    private final AtomicBoolean healthy;
    private volatile long currentToken;
    private volatile Consumer<UaiInfo> infoSink = i -> { };
    private volatile Consumer<String> lineSink;
    private long serial;

    private EnginePlayer(String name, EngineSession session, BlockingQueue<Event> events, AtomicBoolean healthy) {
        this.name = name;
        this.session = session;
        this.events = events;
        this.healthy = healthy;
    }

    /** The options to set: the engine's own, with {@code extra} added or replacing the same names (order kept). */
    static Map<String, String> merge(Map<String, String> own, Map<String, String> extra) {
        Map<String, String> out = new LinkedHashMap<>(own);
        out.putAll(extra);
        return out;
    }

    /**
     * Starts the engine and sets its options. Blocks, so call it from a worker thread.
     *
     * @param extra options for this match only, on top of the configured ones
     * @param lines receives every line sent to and read from the engine (may be null)
     */
    public static EnginePlayer launch(EngineConfig config, Map<String, String> extra, Consumer<String> lines)
            throws UaiException {
        BlockingQueue<Event> events = new LinkedBlockingQueue<>();
        AtomicBoolean healthy = new AtomicBoolean(true);
        EnginePlayer[] holder = new EnginePlayer[1];
        EngineConfig effective = new EngineConfig(config.name(), config.command(), merge(config.options(), extra));
        EngineSession session = EngineSession.launch(effective, new EngineSession.Callbacks() {
            @Override
            public void onInfo(Object token, UaiInfo info) {
                EnginePlayer p = holder[0];
                if (p != null && token != null && token.equals(p.currentToken)) {
                    p.infoSink.accept(info);
                }
            }

            @Override
            public void onBestMove(Object token, Move move) {
                events.add(new Arrived((Long) token, move, System.nanoTime()));
            }

            @Override
            public void onProblem(String message) {
                healthy.set(false);
                events.add(new Problem(message));
            }

            @Override
            public void onLine(UaiClient.Direction direction, String line) {
                EnginePlayer p = holder[0];
                if (p != null && p.lineSink != null) {
                    p.lineSink.accept((direction == UaiClient.Direction.SENT ? ">> " : direction == UaiClient.Direction.ERROR
                            ? "!! " : "<< ") + line);
                }
            }
        });
        EnginePlayer player = new EnginePlayer(session.engineName(), session, events, healthy);
        player.lineSink = lines;
        holder[0] = player;
        return player;
    }

    /** The name the engine gave itself. */
    public String name() {
        return name;
    }

    /** False once the engine has failed or stopped answering; it should be shut down and started again. */
    public boolean isHealthy() {
        return healthy.get() && session.isAlive();
    }

    /** Receives the engine's {@code info} lines for the request in progress (on the client's reader thread). */
    public void setInfoSink(Consumer<UaiInfo> sink) {
        this.infoSink = sink == null ? i -> { } : sink;
    }

    /**
     * Tells the engine a new game begins. Waits up to two seconds for a stopped search to finish first.
     *
     * @throws UaiException if the engine is still busy or does not answer
     */
    public void newGame() throws UaiException {
        for (int i = 0; i < 40 && session.isSearching(); i++) {
            sleep(50);
        }
        events.clear();
        session.newGame();
    }

    /**
     * Asks for a move in {@code start} followed by {@code moves} and waits for it.
     *
     * @param timeoutMs the longest wait for the engine's answer
     * @param cancel checked about twenty times a second; when set the request is abandoned
     * @throws FailedException if there is no answer, the engine failed, or {@code cancel} was set
     */
    public Answer think(Position start, List<Move> moves, GoParams go, long timeoutMs, AtomicBoolean cancel)
            throws FailedException {
        long token = ++serial;
        events.clear();
        currentToken = token;
        long began = System.nanoTime();
        session.requestMove(token, start, moves, go);
        long deadline = began + timeoutMs * 1_000_000L;
        try {
            while (true) {
                if (cancel != null && cancel.get()) {
                    session.cancel();
                    throw new FailedException(Failure.CANCELLED, "cancelled");
                }
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    session.cancel();
                    healthy.set(false);
                    throw new FailedException(Failure.NO_ANSWER, name + " gave no answer within " + timeoutMs + " ms");
                }
                Event e = events.poll(Math.min(left, 50_000_000L), TimeUnit.NANOSECONDS);
                if (e == null) {
                    continue;
                }
                if (e instanceof Problem p) {
                    throw new FailedException(Failure.ENGINE_FAILED, p.message());
                }
                Arrived a = (Arrived) e;
                if (a.token() == token) {
                    return new Answer(a.move(), a.nanos() - began);
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            session.cancel();
            throw new FailedException(Failure.CANCELLED, "interrupted");
        } finally {
            currentToken = 0;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        session.close();
    }
}
