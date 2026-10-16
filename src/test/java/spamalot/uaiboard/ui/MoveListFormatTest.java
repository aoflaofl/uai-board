package spamalot.uaiboard.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Side;

class MoveListFormatTest {
    private static List<Move> moves(String... text) {
        return java.util.Arrays.stream(text).map(Move::parse).toList();
    }

    @Test
    void pairsMovesUnderNumbers() {
        String s = MoveListFormat.format(Side.X, 1, moves("b6", "b2", "a7c5"));
        assertEquals("  1. b6    b2    \n  2. a7c5  \n", s);
    }

    @Test
    void aGameThatStartsWithOShowsAGap() {
        String s = MoveListFormat.format(Side.O, 12, moves("b2", "c6"));
        assertEquals(" 12. ...   b2    \n 13. c6    \n", s);
    }

    @Test
    void noMovesIsEmpty() {
        assertEquals("", MoveListFormat.format(Side.X, 1, List.of()));
    }
}
