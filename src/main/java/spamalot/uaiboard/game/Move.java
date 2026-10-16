package spamalot.uaiboard.game;

/**
 * A move on the 7x7 board: a clone (a new piece on an adjacent square; {@code from} equals {@code to}), a jump (a piece moves
 * two squares) or a pass. Squares are numbered {@code rank * 7 + file} from a1 = 0 to g7 = 48.
 *
 * <p>The text form is the one UAI uses: a clone is its destination ({@code c3}), a jump is source then destination
 * ({@code a1c3}) and a pass is {@code 0000}.
 */
public record Move(int from, int to) {
    private static final Move PASS = new Move(-1, -1);

    public static Move pass() {
        return PASS;
    }

    public static Move clone(int to) {
        return new Move(to, to);
    }

    public static Move jump(int from, int to) {
        return new Move(from, to);
    }

    public boolean isPass() {
        return from < 0;
    }

    public boolean isClone() {
        return from >= 0 && from == to;
    }

    public boolean isJump() {
        return from >= 0 && from != to;
    }

    /** The UAI text form of this move. */
    public String toUai() {
        if (isPass()) {
            return "0000";
        }
        return isClone() ? Squares.name(to) : Squares.name(from) + Squares.name(to);
    }

    /** Parses UAI move text; does not check legality. */
    public static Move parse(String text) {
        if (text.equals("0000")) {
            return PASS;
        }
        if (text.length() == 2) {
            return clone(Squares.parse(text));
        }
        if (text.length() == 4) {
            return jump(Squares.parse(text.substring(0, 2)), Squares.parse(text.substring(2)));
        }
        throw new IllegalArgumentException("not a move: " + text);
    }

    @Override
    public String toString() {
        return toUai();
    }
}
