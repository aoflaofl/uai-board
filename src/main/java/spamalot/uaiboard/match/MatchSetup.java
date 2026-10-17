package spamalot.uaiboard.match;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import spamalot.uaiboard.game.Position;
import spamalot.uaiboard.play.EngineConfig;
import spamalot.uaiboard.play.TimeControl;

/**
 * What a match is: two engines (each with options that apply to this match only), the time control, the number of games and the
 * openings. Every opening is played twice with the colours swapped, so game {@code i} (counting from 0) uses opening
 * {@code i / 2}, with engine A as x in even games and as o in odd ones; a short list of openings is used again for a long match.
 *
 * @param extraA options for engine A only, on top of its saved ones
 * @param extraB options for engine B only
 * @param graceMs how long beyond its time an engine may take before it is given up on
 * @param openings the opening positions; at least one
 */
public record MatchSetup(EngineConfig engineA, Map<String, String> extraA, EngineConfig engineB, Map<String, String> extraB,
                         TimeControl timeControl, int games, List<Position> openings, long graceMs) {
    public MatchSetup {
        if (games < 1) {
            throw new IllegalArgumentException("a match needs at least one game");
        }
        if (openings.isEmpty()) {
            throw new IllegalArgumentException("a match needs at least one opening");
        }
        if (graceMs < 0) {
            throw new IllegalArgumentException("the grace period must not be negative");
        }
        extraA = Map.copyOf(extraA);
        extraB = Map.copyOf(extraB);
        openings = List.copyOf(openings);
    }

    /** The opening of game {@code index} (counting from 0). */
    public Position openingFor(int index) {
        return openings.get((index / 2) % openings.size());
    }

    /** True if engine A is x in game {@code index}. */
    public static boolean aIsX(int index) {
        return index % 2 == 0;
    }

    public String nameA() {
        return label(engineA, extraA);
    }

    public String nameB() {
        return label(engineB, extraB);
    }

    /** The engine's name, with the match-only options in brackets so two entries of one engine can be told apart. */
    private static String label(EngineConfig engine, Map<String, String> extra) {
        if (extra.isEmpty()) {
            return engine.name();
        }
        String options = extra.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", "));
        return engine.name() + " [" + options + "]";
    }
}
