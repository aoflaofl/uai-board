package spamalot.uaiboard.ui;

import java.util.List;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Side;

/** Formats a game's moves as numbered lines of one move pair each ({@code 1. b6 b2}). */
public final class MoveListFormat {
    private MoveListFormat() {}

    /**
     * @param first the side that played {@code moves.get(0)}
     * @param firstNumber the move number of that first move
     */
    public static String format(Side first, int firstNumber, List<Move> moves) {
        StringBuilder sb = new StringBuilder();
        int number = firstNumber;
        Side mover = first;
        for (int i = 0; i < moves.size(); i++) {
            if (mover == Side.X) {
                sb.append(String.format("%3d. ", number));
            } else if (i == 0) {
                sb.append(String.format("%3d. %-6s", number, "..."));
            }
            sb.append(String.format("%-6s", moves.get(i).toUai()));
            if (mover == Side.O || i == moves.size() - 1) {
                sb.append('\n');
            }
            if (mover == Side.O) {
                number++;
            }
            mover = mover.opponent();
        }
        return sb.toString();
    }
}
