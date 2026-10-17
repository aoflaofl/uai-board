package spamalot.uaiboard.uai;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import spamalot.uaiboard.game.Move;

/**
 * What an engine reported in one {@code info} line while searching. Any field the line did not carry is null (the principal
 * variation is empty). Engines differ in which fields they send and in what order, so parsing goes key by key.
 *
 * @param timeMs search time in milliseconds
 * @param score the engine's evaluation from the side to move's point of view
 * @param pv the principal variation
 * @param text the free text after {@code string}
 */
public record UaiInfo(Integer depth, Integer selDepth, Long timeMs, Long nodes, Long nps, Integer hashFull, Score score,
                      List<Move> pv, String text) {

    /** An evaluation: centipawn-style units ({@code cp}) or a forced result in moves ({@code mate}). */
    public record Score(boolean mate, int value) {
        @Override
        public String toString() {
            return (mate ? "mate " : "") + value;
        }
    }

    private static final Set<String> KEYS = Set.of("depth", "seldepth", "time", "nodes", "nps", "hashfull", "score", "pv",
            "string", "currmove", "multipv", "currmovenumber", "tbhits", "cpuload", "refutation", "currline");

    /** Parses an {@code info} line. A line without recognisable fields yields an info with all fields empty. */
    public static UaiInfo parse(String line) {
        String[] t = line.trim().split("\\s+");
        Integer depth = null;
        Integer selDepth = null;
        Long time = null;
        Long nodes = null;
        Long nps = null;
        Integer hashFull = null;
        Score score = null;
        List<Move> pv = new ArrayList<>();
        String text = null;
        int i = t.length > 0 && t[0].equals("info") ? 1 : 0;
        while (i < t.length) {
            String key = t[i];
            if (key.equals("string")) {
                text = String.join(" ", java.util.Arrays.copyOfRange(t, i + 1, t.length));
                break;
            }
            if (key.equals("pv")) {
                for (int j = i + 1; j < t.length && !KEYS.contains(t[j]); j++) {
                    try {
                        pv.add(Move.parse(t[j]));
                    } catch (IllegalArgumentException e) {
                        break;
                    }
                }
                break;
            }
            if (key.equals("score")) {
                // "score cp 12", "score mate 3", or the bare "score 12" some engines print
                int j = i + 1;
                boolean mate = false;
                if (j < t.length && (t[j].equals("cp") || t[j].equals("mate"))) {
                    mate = t[j].equals("mate");
                    j++;
                }
                Long v = j < t.length ? number(t[j]) : null;
                if (v != null && Math.abs(v) <= Integer.MAX_VALUE) {
                    score = new Score(mate, (int) (long) v);
                    j++;
                }
                while (j < t.length && (t[j].equals("lowerbound") || t[j].equals("upperbound"))) {
                    j++;
                }
                i = j;
                continue;
            }
            Long v = i + 1 < t.length ? number(t[i + 1]) : null;
            if (v != null && KEYS.contains(key)) {
                switch (key) {
                    case "depth" -> depth = (int) (long) v;
                    case "seldepth" -> selDepth = (int) (long) v;
                    case "time" -> time = v;
                    case "nodes" -> nodes = v;
                    case "nps" -> nps = v;
                    case "hashfull" -> hashFull = (int) (long) v;
                    default -> { }
                }
                i += 2;
            } else {
                i++;
            }
        }
        return new UaiInfo(depth, selDepth, time, nodes, nps, hashFull, score, List.copyOf(pv), text);
    }

    private static Long number(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
