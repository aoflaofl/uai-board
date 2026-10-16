package spamalot.uaiboard.game;

/** Square numbering and names: {@code rank * 7 + file}, a1 = 0 at the bottom left, g7 = 48 at the top right. */
public final class Squares {
    public static final int SIZE = 7;
    public static final int COUNT = SIZE * SIZE;

    private Squares() {}

    public static int of(int file, int rank) {
        return rank * SIZE + file;
    }

    public static int file(int square) {
        return square % SIZE;
    }

    public static int rank(int square) {
        return square / SIZE;
    }

    /** The name of a square, such as {@code c3}. */
    public static String name(int square) {
        return "" + (char) ('a' + file(square)) + (char) ('1' + rank(square));
    }

    /** The square called {@code name}, such as {@code c3}. */
    public static int parse(String name) {
        if (name.length() != 2) {
            throw new IllegalArgumentException("not a square: " + name);
        }
        int file = name.charAt(0) - 'a';
        int rank = name.charAt(1) - '1';
        if (file < 0 || file >= SIZE || rank < 0 || rank >= SIZE) {
            throw new IllegalArgumentException("not a square: " + name);
        }
        return of(file, rank);
    }

    /** The larger of the file and rank distances between two squares (1 = adjacent, 2 = a jump). */
    public static int distance(int a, int b) {
        return Math.max(Math.abs(file(a) - file(b)), Math.abs(rank(a) - rank(b)));
    }
}
