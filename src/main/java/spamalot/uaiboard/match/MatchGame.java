package spamalot.uaiboard.match;

import spamalot.uaiboard.game.Outcome;

/**
 * One finished game of a match.
 *
 * @param index the game's number counting from 0
 * @param aIsX true if engine A played x
 */
public record MatchGame(int index, boolean aIsX, GameResult result) {
    /** Engine A's score in this game: 1 for a win, 0.5 for a draw, 0 for a loss. */
    public double scoreForA() {
        Outcome o = result.outcome();
        if (o == Outcome.DRAW) {
            return 0.5;
        }
        boolean xWon = o == Outcome.X_WINS;
        return xWon == aIsX ? 1.0 : 0.0;
    }
}
