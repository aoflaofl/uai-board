package spamalot.uaiboard.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import spamalot.uaiboard.game.Side;

class GameClockTest {
    private final AtomicLong now = new AtomicLong();

    private void advanceMs(long ms) {
        now.addAndGet(ms * 1_000_000L);
    }

    @Test
    void onlyTheRunningSideLosesTime() {
        GameClock c = new GameClock(10_000, 0, now::get);
        assertEquals(10_000, c.remaining(Side.X));
        c.start(Side.X);
        advanceMs(3000);
        assertEquals(7000, c.remaining(Side.X));
        assertEquals(10_000, c.remaining(Side.O));
        assertEquals(Side.X, c.running());
    }

    @Test
    void switchingAddsTheIncrementToTheMover() {
        GameClock c = new GameClock(10_000, 500, now::get);
        c.start(Side.X);
        advanceMs(2000);
        c.switchTo(Side.X, Side.O);
        assertEquals(8500, c.remaining(Side.X));
        advanceMs(1000);
        assertEquals(9000, c.remaining(Side.O));
        c.switchTo(Side.O, Side.X);
        assertEquals(9500, c.remaining(Side.O));
        assertEquals(8500, c.remaining(Side.X));
    }

    @Test
    void stopFreezesTheClockAndFlagsFall() {
        GameClock c = new GameClock(1000, 0, now::get);
        c.start(Side.O);
        advanceMs(400);
        assertNull(c.flagged());
        advanceMs(700);
        assertEquals(Side.O, c.flagged());
        c.stop();
        advanceMs(5000);
        assertEquals(-100, c.remaining(Side.O));
        assertNull(c.flagged());
    }

    @Test
    void resumeChangesTheRunningSideWithoutAnIncrement() {
        GameClock c = new GameClock(10_000, 500, now::get);
        c.start(Side.X);
        advanceMs(1000);
        c.resume(Side.O);
        assertEquals(9000, c.remaining(Side.X));
        advanceMs(1000);
        assertEquals(9000, c.remaining(Side.O));
        assertEquals(9000, c.remaining(Side.X));
    }
}
