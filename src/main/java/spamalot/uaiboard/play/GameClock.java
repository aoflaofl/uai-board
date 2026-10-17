package spamalot.uaiboard.play;

import java.util.function.LongSupplier;
import spamalot.uaiboard.game.Side;

/**
 * A game clock for both sides: a base time each and an increment added to the side that has just moved. At most one side's
 * clock runs at a time. The time source is injected so the logic can be tested without waiting.
 */
public final class GameClock {
    private final LongSupplier nanoTime;
    private final long incrementMs;
    private long xMs;
    private long oMs;
    private Side running;
    private long startedNanos;

    public GameClock(long baseMs, long incrementMs, LongSupplier nanoTime) {
        this.xMs = baseMs;
        this.oMs = baseMs;
        this.incrementMs = incrementMs;
        this.nanoTime = nanoTime;
    }

    public long incrementMs() {
        return incrementMs;
    }

    /** Starts {@code side}'s clock (if another side's clock runs it is stopped first, without an increment). */
    public synchronized void start(Side side) {
        charge();
        running = side;
        startedNanos = nanoTime.getAsLong();
    }

    /** Stops whichever clock runs, charging the time used. */
    public synchronized void stop() {
        charge();
        running = null;
    }

    /** {@code mover} has moved: stop its clock, add the increment, and start {@code next}'s clock. */
    public synchronized void switchTo(Side mover, Side next) {
        charge();
        add(mover, incrementMs);
        running = next;
        startedNanos = nanoTime.getAsLong();
    }

    /** Changes the side whose clock runs without an increment (after taking a move back). */
    public synchronized void resume(Side side) {
        charge();
        running = side;
        startedNanos = nanoTime.getAsLong();
    }

    /** Milliseconds left for {@code side} right now; zero or below means its flag has fallen. */
    public synchronized long remaining(Side side) {
        long left = side == Side.X ? xMs : oMs;
        if (running == side) {
            left -= (nanoTime.getAsLong() - startedNanos) / 1_000_000L;
        }
        return left;
    }

    /** The side whose time has run out, or null. */
    public synchronized Side flagged() {
        if (running != null && remaining(running) <= 0) {
            return running;
        }
        return null;
    }

    public synchronized Side running() {
        return running;
    }

    private void charge() {
        if (running != null) {
            long used = (nanoTime.getAsLong() - startedNanos) / 1_000_000L;
            add(running, -used);
            startedNanos = nanoTime.getAsLong();
        }
    }

    private void add(Side side, long ms) {
        if (side == Side.X) {
            xMs += ms;
        } else {
            oMs += ms;
        }
    }
}
