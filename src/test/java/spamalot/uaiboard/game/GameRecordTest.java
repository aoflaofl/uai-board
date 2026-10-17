package spamalot.uaiboard.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameRecordTest {
    private static Game play(Position start, String... moves) {
        Game g = new Game(start);
        for (String m : moves) {
            g.play(Move.parse(m));
        }
        return g;
    }

    @Test
    void textRoundTrips() {
        Game g = play(Position.start(), "b6", "b2", "a7c5", "f6");
        GameRecord r = GameRecord.of(g, "You", "gataxx 1.9.0", "*", LocalDate.of(2026, 10, 17));
        String text = r.toText();
        assertTrue(text.contains("[Date \"2026.10.17\"]"), text);
        assertTrue(text.contains("1. b6 b2 2. a7c5 f6 *"), text);
        GameRecord back = GameRecord.parse(text);
        assertEquals(r.moves(), back.moves());
        assertEquals("gataxx 1.9.0", back.tag("O"));
        assertEquals(g.position(), back.toGame().position());
    }

    @Test
    void aCustomStartIsKeptInAFenTag() {
        Position start = Position.fromFen("x5o/7/7/7/7/7/o5x o 0 7");
        Game g = play(start, "b2", "b6");
        String text = GameRecord.of(g, "a", "b", "*", LocalDate.of(2026, 1, 2)).toText();
        assertTrue(text.contains("[FEN \"x5o/7/7/7/7/7/o5x o 0 7\"]"), text);
        assertTrue(text.contains("7. ... b2 8. b6"), text);
        GameRecord back = GameRecord.parse(text);
        assertEquals(start, back.start());
        assertEquals(g.position(), back.toGame().position());
    }

    @Test
    void parsingAcceptsBareMovesNumbersCommentsAndResults() {
        assertEquals(List.of(Move.parse("b6"), Move.parse("b2"), Move.parse("a7c5")),
                GameRecord.parse("b6 b2 a7c5").moves());
        assertEquals(3, GameRecord.parse("1. b6 {a comment} b2\n2. a7c5 1-0\n").moves().size());
        assertEquals(1, GameRecord.parse("[Result \"*\"]\n\n1. b6").moves().size());
        assertEquals(0, GameRecord.parse("[Event \"x\"]\n").moves().size());
    }

    @Test
    void passesAreKept() {
        // o's piece is boxed in by walls and x pieces, so o must pass.
        Position start = Position.fromFen("7/7/7/7/---4/--x4/o-x4 x 0 1");
        Game g = play(start, "d2", "0000");
        GameRecord r = GameRecord.of(g, "a", "b", "*", LocalDate.of(2026, 1, 2));
        assertTrue(r.toText().contains("0000"), r.toText());
        assertEquals(g.position(), GameRecord.parse(r.toText()).toGame().position());
    }

    @Test
    void badInputIsRejectedWithAMessage() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> GameRecord.parse("1. b6 d4")).getMessage()
                .contains("move 2 (d4) is not legal"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> GameRecord.parse("1. zz")).getMessage()
                .contains("not a move"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> GameRecord.parse("[FEN \"nonsense\"]\n1. b6")).getMessage()
                .contains("bad FEN"));
        assertThrows(IllegalArgumentException.class, () -> GameRecord.parse("   \n"));
    }

    @Test
    void resultTagsFollowTheOutcome() {
        assertEquals("1-0", GameRecord.resultTag(Outcome.X_WINS));
        assertEquals("0-1", GameRecord.resultTag(Outcome.O_WINS));
        assertEquals("1/2-1/2", GameRecord.resultTag(Outcome.DRAW));
        assertEquals("*", GameRecord.resultTag(Outcome.ONGOING));
    }
}
