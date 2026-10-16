package spamalot.uaiboard.game;

import java.util.ArrayList;
import java.util.List;

/** A game in progress: the start position and the moves played, with undo. */
public final class Game {
    private final Position start;
    private final List<Move> moves = new ArrayList<>();
    private final List<Position> positions = new ArrayList<>();

    public Game() {
        this(Position.start());
    }

    public Game(Position start) {
        this.start = start;
        positions.add(start);
    }

    public Position start() {
        return start;
    }

    public Position position() {
        return positions.get(positions.size() - 1);
    }

    /** The moves played so far, oldest first. */
    public List<Move> moves() {
        return List.copyOf(moves);
    }

    public Move lastMove() {
        return moves.isEmpty() ? null : moves.get(moves.size() - 1);
    }

    /** Plays {@code move}, which must be legal in the current position. */
    public void play(Move move) {
        Position next = position().makeMove(move);
        moves.add(move);
        positions.add(next);
    }

    /** Takes back the last move; false if there is none. */
    public boolean undo() {
        if (moves.isEmpty()) {
            return false;
        }
        moves.remove(moves.size() - 1);
        positions.remove(positions.size() - 1);
        return true;
    }
}
