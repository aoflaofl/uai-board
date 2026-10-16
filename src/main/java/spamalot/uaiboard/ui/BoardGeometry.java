package spamalot.uaiboard.ui;

import java.awt.Rectangle;
import spamalot.uaiboard.game.Squares;

/**
 * Where the squares of the board are on screen. Rank 7 is at the top and file a on the left, as in a printed diagram. The
 * board is the largest square that fits in the component, leaving a margin for the coordinate labels, and is centred.
 * Pure arithmetic, so it needs no display.
 */
public final class BoardGeometry {
    private final int cell;
    private final int left;
    private final int top;

    /** @param margin pixels kept free on every side for labels */
    public BoardGeometry(int width, int height, int margin) {
        int room = Math.max(Squares.SIZE, Math.min(width, height) - 2 * margin);
        this.cell = room / Squares.SIZE;
        int side = cell * Squares.SIZE;
        this.left = (width - side) / 2;
        this.top = (height - side) / 2;
    }

    public int cellSize() {
        return cell;
    }

    /** The pixel rectangle of {@code square}. */
    public Rectangle bounds(int square) {
        int row = Squares.SIZE - 1 - Squares.rank(square);
        return new Rectangle(left + Squares.file(square) * cell, top + row * cell, cell, cell);
    }

    /** The whole board. */
    public Rectangle boardBounds() {
        return new Rectangle(left, top, cell * Squares.SIZE, cell * Squares.SIZE);
    }

    /** The square under the pixel, or -1 if it is off the board. */
    public int squareAt(int x, int y) {
        int file = Math.floorDiv(x - left, cell);
        int row = Math.floorDiv(y - top, cell);
        if (file < 0 || file >= Squares.SIZE || row < 0 || row >= Squares.SIZE) {
            return -1;
        }
        return Squares.of(file, Squares.SIZE - 1 - row);
    }
}
