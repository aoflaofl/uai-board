package spamalot.uaiboard.uai;

/**
 * The limits of a {@code go} command; absent values are {@code null}. Clock times are in milliseconds; in UAI X is "black"
 * ({@code btime}, {@code binc}) and O is "white" ({@code wtime}, {@code winc}).
 */
public record GoParams(Long movetime, Integer depth, Long nodes, Long wtime, Long btime, Long winc, Long binc,
                       Integer movesToGo, boolean forever) {
    public static GoParams movetime(long ms) {
        return new GoParams(ms, null, null, null, null, null, null, null, false);
    }

    public static GoParams depth(int depth) {
        return new GoParams(null, depth, null, null, null, null, null, null, false);
    }

    public static GoParams nodes(long nodes) {
        return new GoParams(null, null, nodes, null, null, null, null, null, false);
    }

    public static GoParams infinite() {
        return new GoParams(null, null, null, null, null, null, null, null, true);
    }

    /** Game clock: the time left and the increment of each side, in milliseconds. */
    public static GoParams clock(long xTime, long oTime, long xIncrement, long oIncrement) {
        return new GoParams(null, null, null, oTime, xTime, oIncrement, xIncrement, null, false);
    }

    /** Same limits with a number of moves to the next time control. */
    public GoParams withMovesToGo(int moves) {
        return new GoParams(movetime, depth, nodes, wtime, btime, winc, binc, moves, forever);
    }

    /** The command line to send, such as {@code go movetime 100}. */
    public String toCommand() {
        StringBuilder sb = new StringBuilder("go");
        append(sb, "wtime", wtime);
        append(sb, "btime", btime);
        append(sb, "winc", winc);
        append(sb, "binc", binc);
        append(sb, "movestogo", movesToGo == null ? null : movesToGo.longValue());
        append(sb, "movetime", movetime);
        append(sb, "depth", depth == null ? null : depth.longValue());
        append(sb, "nodes", nodes);
        if (forever) {
            sb.append(" infinite");
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, String key, Long value) {
        if (value != null) {
            sb.append(' ').append(key).append(' ').append(value);
        }
    }
}
