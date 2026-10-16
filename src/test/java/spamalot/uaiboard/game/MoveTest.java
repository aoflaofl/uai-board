package spamalot.uaiboard.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MoveTest {
    @Test
    void textFormsRoundTrip() {
        assertEquals("c3", Move.parse("c3").toUai());
        assertEquals("a1c3", Move.parse("a1c3").toUai());
        assertEquals("0000", Move.parse("0000").toUai());
        assertTrue(Move.parse("c3").isClone());
        assertTrue(Move.parse("a1c3").isJump());
        assertTrue(Move.parse("0000").isPass());
    }

    @Test
    void squaresAreNumberedFromA1() {
        assertEquals(0, Squares.parse("a1"));
        assertEquals(6, Squares.parse("g1"));
        assertEquals(42, Squares.parse("a7"));
        assertEquals(48, Squares.parse("g7"));
        assertEquals("d4", Squares.name(24));
    }

    @Test
    void badTextIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Move.parse("h1"));
        assertThrows(IllegalArgumentException.class, () -> Move.parse("a8"));
        assertThrows(IllegalArgumentException.class, () -> Move.parse("abc"));
    }
}
