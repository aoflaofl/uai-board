package spamalot.uaiboard.game;

/** The two players. X moves first and is "black" in UAI clock terms ({@code btime}); O is "white" ({@code wtime}). */
public enum Side {
    X,
    O;

    public Side opponent() {
        return this == X ? O : X;
    }

    /** The letter used in FEN strings. */
    public char letter() {
        return this == X ? 'x' : 'o';
    }
}
