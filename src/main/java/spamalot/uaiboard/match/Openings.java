package spamalot.uaiboard.match;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Position;

/**
 * Start positions for the games of a match. Every opening is played twice, once with each engine as x, so a match of {@code n}
 * games needs {@link #needed(int)} openings.
 */
public final class Openings {
    private Openings() {}

    /** The number of openings a match of {@code games} games uses (two games per opening, rounding up). */
    public static int needed(int games) {
        return (games + 1) / 2;
    }

    /**
     * {@code count} different openings made by playing {@code plies} random moves from the standard start position. The same
     * seed always gives the same list. Positions in which the game is already over are skipped.
     *
     * @throws IllegalArgumentException if there are fewer than {@code count} different such positions (with very few plies)
     */
    public static List<Position> random(int count, int plies, long seed) {
        if (count < 0 || plies < 0) {
            throw new IllegalArgumentException("count and plies must not be negative");
        }
        Random rnd = new Random(seed);
        List<Position> out = new ArrayList<>();
        Set<Position> seen = new HashSet<>();
        int attempts = 0;
        while (out.size() < count) {
            if (++attempts > 1000 + 200 * count) {
                throw new IllegalArgumentException("cannot make " + count + " different openings of " + plies + " plies");
            }
            Position p = Position.start();
            for (int i = 0; i < plies && !p.isGameOver(); i++) {
                List<Move> moves = p.legalMoves();
                p = p.makeMove(moves.get(rnd.nextInt(moves.size())));
            }
            if (!p.isGameOver() && seen.add(p)) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Reads openings from text, one FEN per line ({@code startpos} means the standard start). Blank lines and lines starting with
     * {@code #} are skipped.
     *
     * @throws IllegalArgumentException with the line number if a line is not a position, or if there are no positions at all
     */
    public static List<Position> parseFens(String text) {
        List<Position> out = new ArrayList<>();
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                out.add(line.equals("startpos") ? Position.start() : Position.fromFen(line));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("line " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("the opening file has no positions");
        }
        return out;
    }

    /**
     * Exactly {@code count} openings taken from {@code pool} in order, starting over when the pool runs out (a short opening file
     * is used again for a long match).
     */
    public static List<Position> select(List<Position> pool, int count) {
        if (pool.isEmpty()) {
            throw new IllegalArgumentException("no openings to choose from");
        }
        List<Position> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(pool.get(i % pool.size()));
        }
        return out;
    }
}
