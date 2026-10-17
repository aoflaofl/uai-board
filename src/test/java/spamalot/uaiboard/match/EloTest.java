package spamalot.uaiboard.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EloTest {
    @Test
    void matchesResultsPrintedByTheGataxxMatchTool() {
        // Records and the Elo lines the gataxx tool printed for them.
        Elo a = Elo.of(606, 0, 594);
        assertEquals(3.5, a.elo(), 0.05);
        assertEquals(19.7, a.margin(), 0.05);
        assertEquals(0.635, a.los(), 0.0006);
        Elo b = Elo.of(1744, 0, 256);
        assertEquals(333.3, b.elo(), 0.05);
        assertEquals(22.8, b.margin(), 0.05);
        Elo c = Elo.of(70, 0, 330);
        assertEquals(-269.4, c.elo(), 0.05);
        assertEquals(44.8, c.margin(), 0.05);
        assertEquals(0.175, c.score(), 1e-9);
        Elo d = Elo.of(1101, 1, 898);
        assertEquals(35.4, d.elo(), 0.05);
        assertEquals(15.3, d.margin(), 0.05);
    }

    @Test
    void drawsCountHalfAndAnEvenRecordIsLevel() {
        Elo e = Elo.of(10, 20, 10);
        assertEquals(0.5, e.score(), 1e-12);
        assertEquals(0, e.elo(), 1e-9);
        assertEquals(0.5, e.los(), 1e-6);
        assertTrue(e.margin() > 0 && e.margin() < Double.POSITIVE_INFINITY);
    }

    @Test
    void noGamesAndPerfectScoresAreHandled() {
        Elo none = Elo.of(0, 0, 0);
        assertEquals(0, none.elo());
        assertTrue(Double.isInfinite(none.margin()));
        assertEquals(0.5, none.los());
        Elo sweep = Elo.of(10, 0, 0);
        assertEquals(Double.POSITIVE_INFINITY, sweep.elo());
        assertTrue(Double.isInfinite(sweep.margin()));
        assertTrue(sweep.los() > 0.99);
        assertEquals(Double.NEGATIVE_INFINITY, Elo.of(0, 0, 10).elo());
        assertEquals(0.5, Elo.of(0, 6, 0).los());
    }

    @Test
    void formatShowsSignAndHandlesInfinity() {
        assertEquals("+3.5 +/- 19.7", Elo.of(606, 0, 594).format());
        assertEquals("-269.4 +/- 44.8", Elo.of(70, 0, 330).format());
        assertEquals("+inf +/- inf", Elo.of(10, 0, 0).format());
        assertEquals("-inf +/- inf", Elo.of(0, 0, 10).format());
    }
}
