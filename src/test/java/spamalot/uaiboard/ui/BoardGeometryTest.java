package spamalot.uaiboard.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Rectangle;
import org.junit.jupiter.api.Test;
import spamalot.uaiboard.game.Squares;

class BoardGeometryTest {
    @Test
    void squaresMapBackFromTheirCentres() {
        BoardGeometry geo = new BoardGeometry(600, 500, 24);
        for (int sq = 0; sq < Squares.COUNT; sq++) {
            Rectangle r = geo.bounds(sq);
            assertEquals(sq, geo.squareAt(r.x + r.width / 2, r.y + r.height / 2));
            assertEquals(sq, geo.squareAt(r.x, r.y));
            assertEquals(sq, geo.squareAt(r.x + r.width - 1, r.y + r.height - 1));
        }
    }

    @Test
    void rankSevenIsAtTheTopAndFileAOnTheLeft() {
        BoardGeometry geo = new BoardGeometry(560, 560, 24);
        Rectangle a7 = geo.bounds(Squares.parse("a7"));
        Rectangle g1 = geo.bounds(Squares.parse("g1"));
        assertEquals(true, a7.x < g1.x && a7.y < g1.y);
    }

    @Test
    void outsideTheBoardIsMinusOne() {
        BoardGeometry geo = new BoardGeometry(560, 560, 24);
        Rectangle board = geo.boardBounds();
        assertEquals(-1, geo.squareAt(board.x - 1, board.y + 5));
        assertEquals(-1, geo.squareAt(board.x + 5, board.y - 1));
        assertEquals(-1, geo.squareAt(board.x + board.width, board.y + 5));
        assertEquals(-1, geo.squareAt(board.x + 5, board.y + board.height));
        assertEquals(-1, geo.squareAt(-50, -50));
    }

    @Test
    void aTinyComponentDoesNotBreak() {
        BoardGeometry geo = new BoardGeometry(10, 10, 24);
        assertEquals(1, geo.cellSize());
    }

    @Test
    void aFlippedBoardIsTurnedHalfACircle() {
        BoardGeometry geo = new BoardGeometry(600, 500, 24, true);
        for (int sq = 0; sq < Squares.COUNT; sq++) {
            Rectangle r = geo.bounds(sq);
            assertEquals(sq, geo.squareAt(r.x + r.width / 2, r.y + r.height / 2));
        }
        BoardGeometry normal = new BoardGeometry(600, 500, 24);
        Rectangle a1 = geo.bounds(Squares.parse("a1"));
        Rectangle g7 = geo.bounds(Squares.parse("g7"));
        assertEquals(true, a1.y < g7.y && a1.x > g7.x); // a1 is at the top right, g7 at the bottom left
        // Each square sits where the opposite square sits on the normal board.
        for (int sq = 0; sq < Squares.COUNT; sq++) {
            assertEquals(normal.bounds(Squares.COUNT - 1 - sq), geo.bounds(sq));
        }
    }
}
