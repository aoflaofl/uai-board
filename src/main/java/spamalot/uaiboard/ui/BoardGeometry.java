package spamalot.uaiboard.ui;

import java.awt.Rectangle;
import spamalot.uaiboard.game.Squares;

/**
 * Where the squares of the board are on screen. Normally rank 7 is at the top and file a on the left, as in a printed diagram;
 * flipped, the board is turned half a circle (rank 1 at the top, file g on the left), as seen from the other side. The board is
 * the largest square that fits in the component, leaving a margin for the coordinate labels, and is centred. Pure arithmetic,
 * so it needs no display.
 */
public final class BoardGeometry {
    private final int cell;
    private final int left;
    private final int top;
    private final boolean flipped;

    /** @param margin pixels kept free on every side for labels */
    public BoardGeometry(int width, int height, int margin) {
        this(width, height, margin, false);
    }

    /** @param flipped true to show the board from the other side */
    public BoardGeometry(int width, int height, int margin, boolean flipped) {
        this.flipped = flipped;
        int room = Math.max(Squares.SIZE, Math.min(width, height) - 2 * margin);
        this.cell = room / Squares.SIZE;
        int side = cell * Squares.SIZE;
        this.left = (width - side) / 2;
        this.top = (height - side) / 2;
    }

    public int cellSize() {
        return cell;
    }

    public boolean isFlipped() {
        return flipped;
    }

    private int column(int square) {
        return flipped ? Squares.SIZE - 1 - Squares.file(square) : Squares.file(square);
    }

    private int row(int square) {
        return flipped ? Squares.rank(square) : Squares.SIZE - 1 - Squares.rank(square);
    }

    /** The pixel rectangle of {@code square}. */
    public Rectangle bounds(int square) {
        return new Rectangle(left + column(square) * cell, top + row(square) * cell, cell, cell);
    }

    /** The whole board. */
    public Rectangle boardBounds() {
        return new Rectangle(left, top, cell * Squares.SIZE, cell * Squares.SIZE);
    }

    /** The square under the pixel, or -1 if it is off the board. */
    public int squareAt(int x, int y) {
        int column = Math.floorDiv(x - left, cell);
        int row = Math.floorDiv(y - top, cell);
        if (column < 0 || column >= Squares.SIZE || row < 0 || row >= Squares.SIZE) {
            return -1;
        }
        return flipped ? Squares.of(Squares.SIZE - 1 - column, row) : Squares.of(column, Squares.SIZE - 1 - row);
    }
}
