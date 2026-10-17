package spamalot.uaiboard.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import spamalot.uaiboard.game.Game;
import spamalot.uaiboard.game.GameRecord;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Outcome;
import spamalot.uaiboard.game.Position;
import spamalot.uaiboard.play.EngineConfig;
import spamalot.uaiboard.play.TimeControl;

class MatchReportTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 17);

    private static MatchGame game(int index, boolean aIsX, Outcome outcome, String termination, String... moves) {
        Game g = new Game();
        for (String m : moves) {
            g.play(Move.parse(m));
        }
        return new MatchGame(index, aIsX, new GameResult(outcome, termination, "", g, 0, 0, 0, 0));
    }

    private static MatchSetup setup() {
        return new MatchSetup(new EngineConfig("gataxx", "g"), Map.of("Hash", "64"), new EngineConfig("Moonbird", "m"), Map.of(),
                new TimeControl.MoveTime(100), 4, List.of(Position.start()), 1000);
    }

    @Test
    void aFinishedGameBecomesARecordWithNamesResultAndRound() {
        // Engine A is o in this game, so the record has the other engine as x.
        GameRecord r = MatchReport.record(setup(), game(1, false, Outcome.O_WINS, "time forfeit", "b6", "b2"), DATE);
        assertEquals("Moonbird", r.tag("X"));
        assertEquals("gataxx [Hash=64]", r.tag("O"));
        assertEquals("0-1", r.tag("Result"));
        assertEquals("time forfeit", r.tag("Termination"));
        assertEquals("2", r.tag("Round"));
        assertEquals(2, r.moves().size());
    }

    @Test
    void theWholeMatchIsOneFileThatReadsBackAsGames() {
        MatchSetup s = setup();
        List<MatchGame> games = List.of(game(0, true, Outcome.X_WINS, "game over", "b6", "b2"),
                game(1, false, Outcome.DRAW, "game over", "b6"), game(2, true, Outcome.O_WINS, "illegal move"));
        Standings standings = Standings.empty().with(games.get(0)).with(games.get(1)).with(games.get(2));
        String text = MatchReport.toText(s, games, standings, "stopped", DATE);
        assertTrue(text.startsWith("# uai-board engine match\n# Date: 2026.10.17\n# Engine A: gataxx [Hash=64]\n"), text);
        assertTrue(text.contains("# Time control: 0.1 s per move (grace 1000 ms)"), text);
        assertTrue(text.contains("# Games: 3 of 4 played (stopped)"), text);
        assertTrue(text.contains("# gataxx [Hash=64]: 1.5 / 3  (+1 =1 -1)  50.0%"), text);
        List<GameRecord> back = GameRecord.parseAll(text);
        assertEquals(3, back.size());
        assertEquals("gataxx [Hash=64]", back.get(0).tag("X"));
        assertEquals("Moonbird", back.get(0).tag("O"));
        assertEquals("Moonbird", back.get(1).tag("X")); // engine A is o in odd games
        assertEquals("1/2-1/2", back.get(1).tag("Result"));
        assertEquals("illegal move", back.get(2).tag("Termination"));
        assertEquals(List.of("1", "2", "3"), back.stream().map(g -> g.tag("Round")).toList());
        assertEquals(games.get(0).result().game().moves(), back.get(0).moves());
    }

    @Test
    void timeControlsAreDescribedInWords() {
        assertEquals("0.1 s per move", new TimeControl.MoveTime(100).describe());
        assertEquals("1 s per move", new TimeControl.MoveTime(1000).describe());
        assertEquals("1.5 s per move", new TimeControl.MoveTime(1500).describe());
        assertEquals("0.025 s per move", new TimeControl.MoveTime(25).describe());
        assertEquals("5 min + 2 s per move", new TimeControl.Clock(300_000, 2000).describe());
        assertEquals("90 s + 0.5 s per move", new TimeControl.Clock(90_000, 500).describe());
        assertEquals("3 s + 0 s per move", new TimeControl.Clock(3000, 0).describe());
    }
}
