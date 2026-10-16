package spamalot.uaiboard.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Position;
import spamalot.uaiboard.game.Squares;

class MoveInputTest {
    private static int sq(String name) {
        return Squares.parse(name);
    }

    @Test
    void selectingThenClickingATargetGivesTheMove() {
        MoveInput in = new MoveInput(Position.start());
        assertEquals(Optional.empty(), in.click(sq("a7")));
        assertEquals(sq("a7"), in.selected());
        assertEquals(8, in.targets().size());
        assertEquals(Optional.of(Move.parse("b6")), in.click(sq("b6")));
        assertEquals(-1, in.selected());
    }

    @Test
    void aJumpTargetGivesAJump() {
        MoveInput in = new MoveInput(Position.start());
        in.click(sq("a7"));
        assertEquals(Optional.of(Move.parse("a7c5")), in.click(sq("c5")));
    }

    @Test
    void clickingAnotherOwnPieceChangesTheSelection() {
        MoveInput in = new MoveInput(Position.start());
        in.click(sq("a7"));
        in.click(sq("g1"));
        assertEquals(sq("g1"), in.selected());
    }

    @Test
    void clickingAnythingElseClearsTheSelection() {
        MoveInput in = new MoveInput(Position.start());
        in.click(sq("a7"));
        assertEquals(Optional.empty(), in.click(sq("g7"))); // the opponent's piece
        assertEquals(-1, in.selected());
        in.click(sq("a7"));
        assertEquals(Optional.empty(), in.click(sq("d4"))); // too far away
        assertEquals(-1, in.selected());
        in.click(sq("a7"));
        assertEquals(Optional.empty(), in.click(-1)); // off the board
        assertTrue(in.targets().isEmpty());
    }

    @Test
    void settingAPositionClearsTheSelection() {
        MoveInput in = new MoveInput(Position.start());
        in.click(sq("a7"));
        in.setPosition(Position.start().makeMove(Move.parse("b6")));
        assertEquals(-1, in.selected());
    }
}
