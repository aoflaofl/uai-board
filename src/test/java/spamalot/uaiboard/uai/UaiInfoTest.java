package spamalot.uaiboard.uai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import spamalot.uaiboard.game.Move;

class UaiInfoTest {
    private static List<Move> moves(String... text) {
        return java.util.Arrays.stream(text).map(Move::parse).toList();
    }

    @Test
    void parsesGataxxLines() {
        UaiInfo i = UaiInfo.parse("info depth 14 score cp 63 nodes 2198170 time 728 nps 3019464 hashfull 31 pv f1 b2 e1 a2 b7");
        assertEquals(14, i.depth());
        assertEquals(new UaiInfo.Score(false, 63), i.score());
        assertEquals(2_198_170L, i.nodes());
        assertEquals(728L, i.timeMs());
        assertEquals(3_019_464L, i.nps());
        assertEquals(31, i.hashFull());
        assertEquals(moves("f1", "b2", "e1", "a2", "b7"), i.pv());
    }

    @Test
    void parsesTikTaxxLines() {
        UaiInfo i = UaiInfo.parse("info depth 8 nodes 363105 score cp 145 time 32 pv b7 a2 f1 a1a3");
        assertEquals(8, i.depth());
        assertEquals(145, i.score().value());
        assertEquals(moves("b7", "a2", "f1", "a1a3"), i.pv());
        assertNull(i.nps());
    }

    @Test
    void parsesMoonbirdLinesWithPaddedNumbers() {
        UaiInfo i = UaiInfo.parse("info depth 15 time     89 nodes    116342 nps 1298230 hashfull   11 score cp   17 pv b6 f6 f2 b2");
        assertEquals(89L, i.timeMs());
        assertEquals(116_342L, i.nodes());
        assertEquals(11, i.hashFull());
        assertEquals(17, i.score().value());
        assertEquals(4, i.pv().size());
    }

    @Test
    void parsesMateBareScoreAndBounds() {
        assertEquals(new UaiInfo.Score(true, -3), UaiInfo.parse("info depth 9 score mate -3 pv a1b2").score());
        assertEquals(new UaiInfo.Score(false, 75), UaiInfo.parse("info depth 9 score 75 nodes 5").score());
        UaiInfo b = UaiInfo.parse("info depth 9 score cp 20 lowerbound nodes 5 pv c3");
        assertEquals(20, b.score().value());
        assertEquals(5L, b.nodes());
        assertEquals(moves("c3"), b.pv());
    }

    @Test
    void parsesStringAndStopsPvAtUnreadableMoves() {
        UaiInfo s = UaiInfo.parse("info string hash table is full");
        assertEquals("hash table is full", s.text());
        assertNull(s.depth());
        assertTrue(s.pv().isEmpty());
        assertEquals(moves("c3", "d4"), UaiInfo.parse("info depth 3 pv c3 d4 zz e5").pv());
    }

    @Test
    void ignoresUnknownKeysAndGarbage() {
        UaiInfo i = UaiInfo.parse("info depth 4 wibble 12 nodes 77");
        assertEquals(4, i.depth());
        assertEquals(77L, i.nodes());
        assertNull(UaiInfo.parse("info").depth());
        assertNull(UaiInfo.parse("info depth").depth());
    }
}
