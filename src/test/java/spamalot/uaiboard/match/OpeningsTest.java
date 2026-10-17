package spamalot.uaiboard.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import spamalot.uaiboard.game.Position;

class OpeningsTest {
    @Test
    void twoGamesPerOpening() {
        assertEquals(0, Openings.needed(0));
        assertEquals(1, Openings.needed(1));
        assertEquals(1, Openings.needed(2));
        assertEquals(500, Openings.needed(1000));
        assertEquals(501, Openings.needed(1001));
    }

    @Test
    void randomOpeningsAreReproducibleDistinctAndLive() {
        List<Position> a = Openings.random(200, 6, 42);
        assertEquals(a, Openings.random(200, 6, 42));
        assertNotEquals(a, Openings.random(200, 6, 43));
        assertEquals(200, new HashSet<>(a).size());
        for (Position p : a) {
            assertFalse(p.isGameOver());
            assertEquals(6, p.fullmoveNumber() * 2 - 2 + (p.sideToMove() == spamalot.uaiboard.game.Side.O ? 1 : 0));
        }
    }

    @Test
    void zeroPliesIsTheStandardStartAndTooManyDifferentOnesFail() {
        assertEquals(List.of(Position.start()), Openings.random(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> Openings.random(2, 0, 1)); // only one such position exists
        assertEquals(0, Openings.random(0, 6, 1).size());
        assertThrows(IllegalArgumentException.class, () -> Openings.random(-1, 6, 1));
    }

    @Test
    void fenFilesAreReadWithCommentsAndErrorsNameTheLine() {
        List<Position> p = Openings.parseFens("# my openings\nstartpos\n\nx5o/7/7/7/7/7/o5x o 0 3  \n");
        assertEquals(2, p.size());
        assertEquals(Position.start(), p.get(0));
        assertEquals("x5o/7/7/7/7/7/o5x o 0 3", p.get(1).toFen());
        String message = assertThrows(IllegalArgumentException.class,
                () -> Openings.parseFens("startpos\n# fine\nnonsense here\n")).getMessage();
        assertTrue(message.startsWith("line 3:"), message);
        assertThrows(IllegalArgumentException.class, () -> Openings.parseFens("# nothing\n\n"));
    }

    @Test
    void selectingCyclesThroughASmallPool() {
        List<Position> pool = Openings.random(3, 4, 7);
        List<Position> chosen = Openings.select(pool, 7);
        assertEquals(7, chosen.size());
        for (int i = 0; i < 7; i++) {
            assertEquals(pool.get(i % 3), chosen.get(i));
        }
        assertEquals(2, Openings.select(pool, 2).size());
        assertThrows(IllegalArgumentException.class, () -> Openings.select(List.of(), 1));
    }
}
