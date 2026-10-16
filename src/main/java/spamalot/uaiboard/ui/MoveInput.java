package spamalot.uaiboard.ui;

import java.util.List;
import java.util.Optional;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Position;

/**
 * Turns clicks on squares into moves: click one of your pieces to select it, then click a highlighted square to clone or
 * jump there. Clicking another of your pieces changes the selection; clicking anything else clears it. Holds no AWT state, so
 * it can be tested without a display.
 */
public final class MoveInput {
    private Position position;
    private int selected = -1;
    private List<Move> targets = List.of();

    public MoveInput(Position position) {
        this.position = position;
    }

    /** Sets the position clicks refer to and clears the selection. */
    public void setPosition(Position position) {
        this.position = position;
        clear();
    }

    public void clear() {
        selected = -1;
        targets = List.of();
    }

    /** The selected square, or -1. */
    public int selected() {
        return selected;
    }

    /** The moves the selected piece can make; a clone's destination is next to the piece, a jump's two steps away. */
    public List<Move> targets() {
        return targets;
    }

    /** Handles a click on {@code square} (-1 for outside the board); returns the move if the click completed one. */
    public Optional<Move> click(int square) {
        if (square < 0) {
            clear();
            return Optional.empty();
        }
        for (Move m : targets) {
            if (m.to() == square) {
                clear();
                return Optional.of(m);
            }
        }
        List<Move> own = position.movesFrom(square);
        if (own.isEmpty()) {
            clear();
        } else {
            selected = square;
            targets = own;
        }
        return Optional.empty();
    }
}
