package spamalot.uaiboard.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PositionTest {
    private static long perft(Position p, int depth) {
        if (depth == 0) {
            return 1;
        }
        long nodes = 0;
        for (Move m : p.legalMoves()) {
            nodes += perft(p.makeMove(m), depth - 1);
        }
        return nodes;
    }

    @Test
    void perftMatchesTheReferenceCounts() {
        // The same numbers as gataxx and libataxx give from the start position.
        Position start = Position.start();
        assertEquals(16, perft(start, 1));
        assertEquals(256, perft(start, 2));
        assertEquals(6460, perft(start, 3));
        assertEquals(155_888, perft(start, 4));
    }

    @Test
    void fenRoundTrips() {
        assertEquals(Position.START_FEN, Position.start().toFen());
        String fen = "x5o/2-4/7/3x3/7/7/o5x o 7 12";
        assertEquals(fen, Position.fromFen(fen).toFen());
    }

    @Test
    void badFensAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Position.fromFen("x5o/7/7/7/7/7 x 0 1"));
        assertThrows(IllegalArgumentException.class, () -> Position.fromFen("x5q/7/7/7/7/7/o5x x 0 1"));
        assertThrows(IllegalArgumentException.class, () -> Position.fromFen("x6o/7/7/7/7/7/o5x x 0 1"));
        assertThrows(IllegalArgumentException.class, () -> Position.fromFen("x5o/7/7/7/7/7/o5x z 0 1"));
    }

    @Test
    void cloneKeepsTheSourceAndConvertsNeighbours() {
        // x on a1; o on b3 and c3 (and g7, so the game goes on). Cloning to b2 converts b3 and c3, both next to b2.
        Position p = Position.fromFen("6o/7/7/7/1oo4/7/x6 x 0 1");
        Position after = p.makeMove(Move.parse("b2"));
        assertEquals(Position.Piece.X, after.pieceAt(Squares.parse("a1")));
        assertEquals(Position.Piece.X, after.pieceAt(Squares.parse("b2")));
        assertEquals(Position.Piece.X, after.pieceAt(Squares.parse("b3")));
        assertEquals(Position.Piece.X, after.pieceAt(Squares.parse("c3")));
        assertEquals(Position.Piece.O, after.pieceAt(Squares.parse("g7")));
        assertEquals(0, after.halfmoveClock());
        assertEquals(Side.O, after.sideToMove());
        assertEquals(2, p.conversions(Move.parse("b2")));
    }

    @Test
    void jumpLeavesTheSourceEmptyAndCountsAHalfmove() {
        Position p = Position.fromFen("6o/7/7/7/7/7/x6 x 0 1");
        Position after = p.makeMove(Move.parse("a1c1"));
        assertEquals(Position.Piece.EMPTY, after.pieceAt(Squares.parse("a1")));
        assertEquals(Position.Piece.X, after.pieceAt(Squares.parse("c1")));
        assertEquals(1, after.halfmoveClock());
    }

    @Test
    void capturesConvertEveryAdjacentEnemy() {
        // Cloning to b2 converts a3, b3 and c3, the three squares next to it on rank 3.
        Position p = Position.fromFen("6o/7/7/7/ooo4/7/x6 x 0 1");
        Position after = p.makeMove(Move.parse("b2"));
        assertEquals(Position.Piece.X, after.pieceAt(Squares.parse("a3")));
        assertEquals(Position.Piece.X, after.pieceAt(Squares.parse("b3")));
        assertEquals(Position.Piece.X, after.pieceAt(Squares.parse("c3")));
        assertEquals(1, after.count(Side.O));
        assertEquals(Outcome.ONGOING, after.outcome());
    }

    @Test
    void aStuckSidePasses() {
        // o's lone piece in the corner is boxed in by walls and x pieces, so it cannot move; x can.
        Position p = Position.fromFen("7/7/7/7/---4/--x4/o-x4 o 0 1");
        assertFalse(p.isGameOver());
        assertEquals(List.of(Move.pass()), p.legalMoves());
        Position after = p.makeMove(Move.pass());
        assertEquals(Side.X, after.sideToMove());
        assertEquals(1, after.halfmoveClock());
    }

    @Test
    void illegalMovesAreRejected() {
        Position p = Position.start();
        assertThrows(IllegalArgumentException.class, () -> p.makeMove(Move.parse("a1a2")));
        assertThrows(IllegalArgumentException.class, () -> p.makeMove(Move.parse("d4")));
        assertFalse(p.isLegal(Move.pass()));
    }

    @Test
    void gameEndsWhenASideIsWipedOutOrNobodyCanMove() {
        Position full = Position.fromFen("xxxxxxx/xxxxxxx/xxxxxxx/xxxxxxx/xxxxxxx/xxxxxxx/xxxxxxo x 0 1");
        assertTrue(full.isGameOver());
        assertEquals(Outcome.X_WINS, full.outcome());
        assertEquals(Outcome.O_WINS, Position.fromFen("7/7/7/7/7/7/oo5 x 0 1").outcome());
    }

    @Test
    void hundredHalfmovesWithoutACloneEndTheGame() {
        Position p = Position.fromFen("x5o/7/7/7/7/7/o5x x 100 60");
        assertTrue(p.isGameOver());
        assertEquals(Outcome.DRAW, p.outcome());
        assertTrue(p.legalMoves().isEmpty());
    }

    @Test
    void movesFromListsClonesThenJumpsOfThatPiece() {
        Position p = Position.start();
        List<Move> moves = p.movesFrom(Squares.parse("a7"));
        assertEquals(3 + 5, moves.size()); // a7 is x: 3 empty neighbours, 5 empty squares two away (corner: 5)
        assertTrue(moves.get(0).isClone());
        assertTrue(moves.get(moves.size() - 1).isJump());
        assertTrue(p.movesFrom(Squares.parse("g7")).isEmpty()); // o's piece, x to move
        assertTrue(p.movesFrom(Squares.parse("d4")).isEmpty()); // empty square
    }
}
