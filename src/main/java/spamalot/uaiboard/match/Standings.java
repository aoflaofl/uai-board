package spamalot.uaiboard.match;

import java.util.Map;

/**
 * The running score of a match, from engine A's point of view.
 *
 * @param terminations how many games ended each way ({@code game over}, {@code time forfeit}, {@code illegal move}, ...)
 * @param aNanos the time engine A spent thinking, in nanoseconds
 * @param bNanos the time engine B spent thinking
 * @param aMoves the number of moves engine A made
 * @param bMoves the number of moves engine B made
 */
public record Standings(int wins, int draws, int losses, Map<String, Integer> terminations, long aNanos, long bNanos,
                        int aMoves, int bMoves) {
    public static Standings empty() {
        return new Standings(0, 0, 0, Map.of(), 0, 0, 0, 0);
    }

    public Standings {
        terminations = Map.copyOf(terminations);
    }

    /** Games played so far. */
    public int games() {
        return wins + draws + losses;
    }

    /** Engine A's score in games (a draw counts a half). */
    public double points() {
        return wins + draws / 2.0;
    }

    /** The Elo difference of A over B with its error margin and LOS. */
    public Elo elo() {
        return Elo.of(wins, draws, losses);
    }

    /** The average time of A's moves in milliseconds, or 0 if it has made none. */
    public double averageMillisA() {
        return aMoves == 0 ? 0 : aNanos / 1e6 / aMoves;
    }

    /** The average time of B's moves in milliseconds, or 0 if it has made none. */
    public double averageMillisB() {
        return bMoves == 0 ? 0 : bNanos / 1e6 / bMoves;
    }

    /** The standings after another finished game. */
    Standings with(MatchGame game) {
        double s = game.scoreForA();
        Map<String, Integer> t = new java.util.HashMap<>(terminations);
        t.merge(game.result().termination(), 1, Integer::sum);
        GameResult r = game.result();
        boolean aX = game.aIsX();
        return new Standings(wins + (s == 1.0 ? 1 : 0), draws + (s == 0.5 ? 1 : 0), losses + (s == 0.0 ? 1 : 0), t,
                aNanos + (aX ? r.xNanos() : r.oNanos()), bNanos + (aX ? r.oNanos() : r.xNanos()),
                aMoves + (aX ? r.xMoves() : r.oMoves()), bMoves + (aX ? r.oMoves() : r.xMoves()));
    }
}
