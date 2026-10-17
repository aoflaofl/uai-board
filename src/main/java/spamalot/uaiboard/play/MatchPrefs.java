package spamalot.uaiboard.play;

/**
 * The last settings of the match window, kept between runs.
 *
 * @param engineA name of engine A (null if none was chosen)
 * @param engineB name of engine B (null if none was chosen)
 * @param timeControl move time or game clock for both engines
 * @param games number of games
 * @param plies random plies of each opening
 * @param seed seed of the random openings
 * @param graceMs how long beyond its time an engine may take
 * @param extraA match-only options of engine A, one {@code Name=value} per line
 * @param extraB match-only options of engine B
 * @param openingFile path of a file of opening FENs, or empty to use random openings
 */
public record MatchPrefs(String engineA, String engineB, TimeControl timeControl, int games, int plies, long seed,
                         long graceMs, String extraA, String extraB, String openingFile) {
    public static final MatchPrefs DEFAULT = new MatchPrefs(null, null, new TimeControl.MoveTime(100), 20, 6, 1, 1000, "", "",
            "");
}
