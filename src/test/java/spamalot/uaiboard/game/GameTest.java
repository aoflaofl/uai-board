package spamalot.uaiboard.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GameTest {
    @Test
    void playAndUndo() {
        Game g = new Game();
        assertNull(g.lastMove());
        g.play(Move.parse("b6"));
        g.play(Move.parse("b2"));
        assertEquals(2, g.moves().size());
        assertEquals(Move.parse("b2"), g.lastMove());
        assertTrue(g.undo());
        assertEquals(Move.parse("b6"), g.lastMove());
        assertTrue(g.undo());
        assertFalse(g.undo());
        assertEquals(Position.start(), g.position());
    }
}
