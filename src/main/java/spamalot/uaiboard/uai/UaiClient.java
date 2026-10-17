package spamalot.uaiboard.uai;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Position;

/**
 * Drives an external UAI engine process: handshake, options, positions and searches.
 *
 * <p>Nothing here touches Swing. Replies that arrive on their own, such as {@code info} lines and the {@code bestmove} that ends
 * a search, are delivered to the {@link Listener} on the client's reader thread, so a user interface must hand them to the
 * event dispatch thread itself. Commands that wait for an answer ({@link #handshake}, {@link #waitReady}, {@link #setOption},
 * {@link #newGame}) block the calling thread, so call them from a worker thread; {@link #go} and {@link #stop} return at once.
 */
public final class UaiClient implements AutoCloseable {
    /** Which way a logged line travelled. */
    public enum Direction {
        /** A command sent to the engine. */
        SENT,
        /** A line read from the engine's output. */
        RECEIVED,
        /** A line the engine wrote to its error output, or a problem the client noticed. */
        ERROR
    }

    /** Receives what the engine says. All methods run on the client's reader thread. */
    public interface Listener {
        default void onInfo(UaiInfo info) { }

        /** The search ended: the engine's chosen move (possibly a pass). */
        default void onBestMove(Move move) { }

        /** Every line, in both directions, for an engine console. */
        default void onLine(Direction direction, String line) { }

        /** The engine said something that could not be understood, such as an unreadable {@code bestmove}. */
        default void onProtocolError(String message) { }

        /** The engine's output closed, which means it has exited (exit code, or -1 if unknown). */
        default void onExit(int exitCode) { }
    }

    /** What an engine announced during the handshake. */
    public record EngineId(String name, String author, List<UaiOption> options) { }

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final String EXITED = "\0exited";

    private final Process process;
    private final BufferedWriter stdin;
    private final Listener listener;
    private final BlockingQueue<String> control = new LinkedBlockingQueue<>();
    private volatile boolean exited;
    private volatile boolean closing;
    private boolean fenOnly;

    private UaiClient(Process process, Listener listener) {
        this.process = process;
        this.listener = listener;
        this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        daemon("uai-reader", this::readOutput);
        daemon("uai-stderr", this::readErrors);
    }

    /** Launches the engine; call {@link #handshake} next. {@code command} is the program and its arguments. */
    public static UaiClient start(List<String> command, Listener listener) throws UaiException {
        if (command.isEmpty()) {
            throw new UaiException("no engine command given");
        }
        try {
            return new UaiClient(new ProcessBuilder(command).start(), listener);
        } catch (IOException e) {
            throw new UaiException("cannot start " + String.join(" ", command) + ": " + e.getMessage(), e);
        }
    }

    /**
     * Send every position as a bare FEN with the moves already applied, instead of {@code fen ... moves ...}, for engines that
     * do not read move lists reliably.
     */
    public void setFenOnly(boolean fenOnly) {
        this.fenOnly = fenOnly;
    }

    private static void daemon(String name, Runnable body) {
        Thread t = new Thread(body, name);
        t.setDaemon(true);
        t.start();
    }

    private void readOutput() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                dispatch(line);
            }
        } catch (IOException e) {
            // The stream closing is treated as the engine going away.
        }
        exited = true;
        control.add(EXITED);
        int code = -1;
        try {
            if (process.waitFor(2, TimeUnit.SECONDS)) {
                code = process.exitValue();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int exitCode = code;
        call(() -> listener.onExit(exitCode));
    }

    private void readErrors() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String l = line;
                call(() -> listener.onLine(Direction.ERROR, l));
            }
        } catch (IOException e) {
            // Nothing more to read.
        }
    }

    private void dispatch(String line) {
        call(() -> listener.onLine(Direction.RECEIVED, line));
        String trimmed = line.trim();
        String word = trimmed.contains(" ") ? trimmed.substring(0, trimmed.indexOf(' ')) : trimmed;
        switch (word) {
            case "info" -> {
                UaiInfo info = UaiInfo.parse(trimmed);
                call(() -> listener.onInfo(info));
            }
            case "bestmove" -> {
                String[] t = trimmed.split("\\s+");
                try {
                    Move move = Move.parse(t.length > 1 ? t[1] : "");
                    call(() -> listener.onBestMove(move));
                } catch (IllegalArgumentException e) {
                    call(() -> listener.onProtocolError("unreadable bestmove: " + trimmed));
                }
            }
            default -> control.add(trimmed);
        }
    }

    /** Runs a listener callback; a failing callback must not kill the reader thread. */
    private void call(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException e) {
            // A misbehaving listener is its own problem.
        }
    }

    private synchronized void send(String command) throws UaiException {
        if (exited) {
            throw new UaiException("the engine has exited");
        }
        try {
            stdin.write(command);
            stdin.newLine();
            stdin.flush();
        } catch (IOException e) {
            throw new UaiException("cannot write to the engine: " + e.getMessage(), e);
        }
        call(() -> listener.onLine(Direction.SENT, command));
    }

    /** Waits for the line {@code expected} and returns every other line that came first. */
    private List<String> waitFor(String expected, Duration timeout) throws UaiException {
        long deadline = System.nanoTime() + timeout.toNanos();
        List<String> before = new ArrayList<>();
        while (true) {
            long left = deadline - System.nanoTime();
            String line;
            try {
                line = left <= 0 ? control.poll() : control.poll(left, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UaiException("interrupted while waiting for " + expected, e);
            }
            if (line == null) {
                throw new UaiException("the engine did not answer " + expected + " within " + timeout.toMillis() + " ms");
            }
            if (line.equals(EXITED)) {
                control.add(EXITED); // later waiters must see it too
                throw new UaiException("the engine exited while waiting for " + expected);
            }
            if (line.equals(expected)) {
                return before;
            }
            before.add(line);
        }
    }

    /** Sends {@code uai} and reads the engine's name, author and options up to {@code uaiok}. */
    public EngineId handshake(Duration timeout) throws UaiException {
        send("uai");
        String name = "";
        String author = "";
        List<UaiOption> options = new ArrayList<>();
        for (String line : waitFor("uaiok", timeout)) {
            if (line.startsWith("id name ")) {
                name = line.substring("id name ".length()).trim();
            } else if (line.startsWith("id author ")) {
                author = line.substring("id author ".length()).trim();
            } else if (line.startsWith("option ")) {
                UaiOption option = UaiOption.parse(line);
                if (option != null) {
                    options.add(option);
                }
            }
        }
        return new EngineId(name, author, List.copyOf(options));
    }

    /** Sends {@code isready} and waits for {@code readyok}. */
    public void waitReady(Duration timeout) throws UaiException {
        send("isready");
        waitFor("readyok", timeout);
    }

    /** Sends {@code setoption name <name> value <value>} and waits until the engine has taken it. */
    public void setOption(String name, String value) throws UaiException {
        send("setoption name " + name + " value " + value);
        waitReady(DEFAULT_TIMEOUT);
    }

    /** Sends a button option, which has no value. */
    public void pressButton(String name) throws UaiException {
        send("setoption name " + name);
        waitReady(DEFAULT_TIMEOUT);
    }

    /** Tells the engine a new game starts (clearing its hash table and the like) and waits until it is ready. */
    public void newGame() throws UaiException {
        send("uainewgame");
        waitReady(DEFAULT_TIMEOUT);
    }

    /** The {@code position} command for {@code start} followed by {@code moves}. */
    String positionCommand(Position start, List<Move> moves) {
        if (fenOnly) {
            Position p = start;
            for (Move m : moves) {
                p = p.makeMove(m);
            }
            return "position fen " + p.toFen();
        }
        StringBuilder sb = new StringBuilder(start.equals(Position.start()) ? "position startpos" : "position fen " + start.toFen());
        if (!moves.isEmpty()) {
            sb.append(" moves");
            for (Move m : moves) {
                sb.append(' ').append(m.toUai());
            }
        }
        return sb.toString();
    }

    /** Sets the position the next search starts from: {@code start} with {@code moves} played. */
    public void setPosition(Position start, List<Move> moves) throws UaiException {
        send(positionCommand(start, moves));
    }

    /** Starts a search; the answer arrives through {@link Listener#onBestMove}. */
    public void go(GoParams params) throws UaiException {
        send(params.toCommand());
    }

    /** Asks the engine to end the search now; it still answers with {@code bestmove}. */
    public void stop() throws UaiException {
        send("stop");
    }

    public boolean isAlive() {
        return !exited && process.isAlive();
    }

    /** Asks the engine to quit, then makes sure the process is gone. Safe to call more than once. */
    @Override
    public void close() {
        if (closing) {
            return;
        }
        closing = true;
        try {
            if (!exited) {
                send("quit");
            }
        } catch (UaiException e) {
            // It is going away anyway.
        }
        try {
            if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                process.destroy();
                if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
