package spamalot.uaiboard.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The standings of a match as lines of text, for the match window and for saved results. */
public final class StandingsText {
    private StandingsText() {}

    /**
     * The lines: who played, the score, the Elo difference, how the games ended and the average move times. Engine A is the
     * first name; its score and Elo are the ones shown.
     */
    public static List<String> lines(String nameA, String nameB, Standings s) {
        List<String> out = new ArrayList<>();
        out.add(nameA + " vs " + nameB);
        int n = s.games();
        out.add(String.format("%s: %.1f / %d  (+%d =%d -%d)  %.1f%%", nameA, s.points(), n, s.wins(), s.draws(), s.losses(),
                n == 0 ? 0.0 : 100.0 * s.points() / n));
        Elo elo = s.elo();
        out.add(String.format("Elo %s   LOS %.1f%%", elo.format(), elo.los() * 100));
        if (!s.terminations().isEmpty()) {
            StringBuilder sb = new StringBuilder("Games ended by: ");
            boolean first = true;
            for (Map.Entry<String, Integer> e : new TreeMap<>(s.terminations()).entrySet()) {
                sb.append(first ? "" : ", ").append(e.getKey()).append(' ').append(e.getValue());
                first = false;
            }
            out.add(sb.toString());
        }
        if (s.aMoves() + s.bMoves() > 0) {
            out.add(String.format("Average move time: %s %.0f ms, %s %.0f ms", nameA, s.averageMillisA(), nameB,
                    s.averageMillisB()));
        }
        return out;
    }
}
