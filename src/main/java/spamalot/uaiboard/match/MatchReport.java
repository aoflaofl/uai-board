package spamalot.uaiboard.match;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import spamalot.uaiboard.game.GameRecord;

/** A match as text: a summary at the top and every finished game below it, in the game file format. */
public final class MatchReport {
    private MatchReport() {}

    /** The finished game as a game record, with the engines' names, the result, how it ended and the round number. */
    public static GameRecord record(MatchSetup setup, MatchGame game, LocalDate date) {
        String x = game.aIsX() ? setup.nameA() : setup.nameB();
        String o = game.aIsX() ? setup.nameB() : setup.nameA();
        GameResult r = game.result();
        return GameRecord.of(r.game(), x, o, GameRecord.resultTag(r.outcome()), date, r.termination())
                .withTag("Round", String.valueOf(game.index() + 1));
    }

    /**
     * The whole match as one file: comment lines with the engines, the time control and the standings, then the games.
     *
     * @param status how the match stands, such as {@code finished} or {@code stopped}
     */
    public static String toText(MatchSetup setup, List<MatchGame> games, Standings standings, String status, LocalDate date) {
        List<String> header = new ArrayList<>();
        header.add("uai-board engine match");
        header.add("Date: " + String.format("%04d.%02d.%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth()));
        header.add("Engine A: " + setup.nameA());
        header.add("Engine B: " + setup.nameB());
        header.add("Time control: " + setup.timeControl().describe() + " (grace " + setup.graceMs() + " ms)");
        header.add("Games: " + games.size() + " of " + setup.games() + " played (" + status + ")");
        List<String> standingsLines = StandingsText.lines(setup.nameA(), setup.nameB(), standings);
        header.addAll(standingsLines.subList(1, standingsLines.size()));
        List<GameRecord> records = new ArrayList<>();
        for (MatchGame g : games) {
            records.add(record(setup, g, date));
        }
        return GameRecord.toText(records, header);
    }
}
