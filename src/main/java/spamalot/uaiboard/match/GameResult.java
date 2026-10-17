package spamalot.uaiboard.match;

import spamalot.uaiboard.game.Game;
import spamalot.uaiboard.game.Outcome;

/**
 * How one game of a match ended.
 *
 * @param outcome who won ({@code ONGOING} only when the game was cancelled)
 * @param termination why the game ended: {@code game over} (by the rules), {@code time forfeit} (a flag fell), {@code timeout}
 *     (no answer within the move time and the grace period), {@code illegal move}, {@code engine failure} (the engine exited or
 *     wrote something unreadable), {@code ply limit} or {@code cancelled}
 * @param detail a message for the log, empty for a normal end
 * @param game the start position and the moves played
 * @param xNanos the time x's engine spent thinking, in nanoseconds
 * @param oNanos the time o's engine spent thinking, in nanoseconds
 * @param xMoves the number of moves x's engine made
 * @param oMoves the number of moves o's engine made
 */
public record GameResult(Outcome outcome, String termination, String detail, Game game, long xNanos, long oNanos, int xMoves,
                         int oMoves) {
    /** True if the game was played to a result (not cancelled). */
    public boolean finished() {
        return outcome != Outcome.ONGOING;
    }
}
