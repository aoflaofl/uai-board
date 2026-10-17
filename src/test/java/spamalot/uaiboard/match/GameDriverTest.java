package spamalot.uaiboard.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import spamalot.uaiboard.game.Game;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Outcome;
import spamalot.uaiboard.game.Position;
import spamalot.uaiboard.game.Side;
import spamalot.uaiboard.play.FakeEngines;
import spamalot.uaiboard.play.TimeControl;
import spamalot.uaiboard.uai.UaiException;
import spamalot.uaiboard.uai.UaiInfo;

/** Games between {@link spamalot.uaiboard.uai.FakeEngine}s, which play the first legal move (or misbehave on request). */
class GameDriverTest {
    private static final long GRACE = 1000;
    private final List<EnginePlayer> players = new ArrayList<>();

    @AfterEach
    void closePlayers() {
        players.forEach(EnginePlayer::close);
    }

    private EnginePlayer player(String mode) throws UaiException {
        EnginePlayer p = EnginePlayer.launch(FakeEngines.config(mode), Map.of(), null);
        players.add(p);
        return p;
    }

    private static class Recorder implements GameDriver.Observer {
        final List<String> requested = Collections.synchronizedList(new ArrayList<>());
        final List<Move> moved = Collections.synchronizedList(new ArrayList<>());
        final List<Side> infoSides = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void moveRequested(Game game, Side side, long xRemainingMs, long oRemainingMs) {
            requested.add(side + ":" + xRemainingMs + ":" + oRemainingMs);
        }

        @Override
        public void info(Side side, UaiInfo info) {
            infoSides.add(side);
        }

        @Override
        public void moved(Game game, Move move, Side mover, long nanos) {
            moved.add(move);
        }
    }

    private static final TimeControl FAST = new TimeControl.MoveTime(50);

    @Test
    void aWholeGameIsPlayedToTheEndByTheRules() throws Exception {
        EnginePlayer x = player("normal");
        EnginePlayer o = player("normal");
        Recorder rec = new Recorder();
        GameResult r = GameDriver.play(Position.start(), x, o, FAST, GRACE, rec, new AtomicBoolean());
        assertEquals("game over", r.termination());
        assertTrue(r.finished());
        assertTrue(r.game().position().isGameOver());
        assertEquals(r.game().position().outcome(), r.outcome());
        assertTrue(r.game().moves().size() > 10);
        assertEquals(r.game().moves(), rec.moved);
        assertEquals(r.xMoves() + r.oMoves(), r.game().moves().size());
        assertTrue(r.xNanos() > 0 && r.oNanos() > 0);
        assertTrue(rec.infoSides.contains(Side.X) && rec.infoSides.contains(Side.O), rec.infoSides.toString());
        assertTrue(x.isHealthy() && o.isHealthy());
    }

    @Test
    void gamesStartFromTheGivenOpeningAndEnginesSeeTheMoves() throws Exception {
        // o is to move in this opening, so o's engine is asked first.
        Position opening = Position.fromFen("x5o/7/7/7/7/7/o5x o 0 5");
        Recorder rec = new Recorder();
        GameResult r = GameDriver.play(opening, player("normal"), player("normal"), FAST, GRACE, rec, new AtomicBoolean());
        assertEquals(opening, r.game().start());
        assertTrue(rec.requested.get(0).startsWith("O:"), rec.requested.get(0));
        assertTrue(rec.requested.get(1).startsWith("X:"), rec.requested.get(1));
        assertEquals("game over", r.termination());
        assertEquals(opening, new Game(opening).start());
        Game replay = new Game(opening);
        r.game().moves().forEach(replay::play); // every move was legal from the opening
        assertEquals(r.game().position(), replay.position());
    }

    @Test
    void aSideWithNoMovePassesAutomatically() throws Exception {
        // o is boxed in and to move: the first move of the game is a pass.
        Position stuck = Position.fromFen("7/7/7/7/---4/--x4/o-x4 o 0 1");
        Recorder rec = new Recorder();
        GameResult r = GameDriver.play(stuck, player("normal"), player("normal"), FAST, GRACE, rec, new AtomicBoolean());
        assertEquals(Move.pass(), r.game().moves().get(0));
        assertEquals(Move.pass(), rec.moved.get(0));
        assertEquals("game over", r.termination());
        assertEquals(Outcome.X_WINS, r.outcome());
        assertFalse(rec.requested.get(0).startsWith("O"), "o was never asked for a move");
    }

    @Test
    void anIllegalMoveLosesTheGame() throws Exception {
        GameResult r = GameDriver.play(Position.start(), player("illegal"), player("normal"), FAST, GRACE, new Recorder(),
                new AtomicBoolean());
        assertEquals("illegal move", r.termination());
        assertEquals(Outcome.O_WINS, r.outcome());
        assertTrue(r.game().moves().isEmpty());
        assertTrue(r.detail().contains("d4"), r.detail());
        // The same engine as o loses after x's first move.
        GameResult r2 = GameDriver.play(Position.start(), player("normal"), player("illegal"), FAST, GRACE, new Recorder(),
                new AtomicBoolean());
        assertEquals(Outcome.X_WINS, r2.outcome());
        assertEquals(1, r2.game().moves().size());
    }

    @Test
    void anUnreadableMoveAndACrashAreEngineFailures() throws Exception {
        EnginePlayer bad = player("badmove");
        GameResult r = GameDriver.play(Position.start(), bad, player("normal"), FAST, GRACE, new Recorder(),
                new AtomicBoolean());
        assertEquals("engine failure", r.termination());
        assertEquals(Outcome.O_WINS, r.outcome());
        assertFalse(bad.isHealthy());

        EnginePlayer crash = player("crash");
        GameResult r2 = GameDriver.play(Position.start(), player("normal"), crash, FAST, GRACE, new Recorder(),
                new AtomicBoolean());
        assertEquals("engine failure", r2.termination());
        assertEquals(Outcome.X_WINS, r2.outcome());
        assertTrue(r2.detail().contains("exit"), r2.detail());
        assertFalse(crash.isHealthy());
    }

    @Test
    void anEngineThatNeverAnswersTimesOut() throws Exception {
        EnginePlayer mute = player("mute");
        GameResult r = GameDriver.play(Position.start(), mute, player("normal"), new TimeControl.MoveTime(50), 150,
                new Recorder(), new AtomicBoolean());
        assertEquals("timeout", r.termination());
        assertEquals(Outcome.O_WINS, r.outcome());
        assertFalse(mute.isHealthy());
    }

    @Test
    void aSlowAnswerBeyondTheGraceIsATimeout() throws Exception {
        // The slow engine takes 400 ms; 50 ms plus 100 ms of grace is not enough.
        GameResult r = GameDriver.play(Position.start(), player("slow"), player("normal"), new TimeControl.MoveTime(50), 100,
                new Recorder(), new AtomicBoolean());
        assertEquals("timeout", r.termination());
        assertEquals(Outcome.O_WINS, r.outcome());
    }

    @Test
    void aSlowAnswerWithinTheGraceIsAccepted() throws Exception {
        // The slow engine takes 400 ms, well inside 50 ms plus 2000 ms of grace; stop after its first move.
        AtomicBoolean cancel = new AtomicBoolean();
        Recorder rec = new Recorder() {
            @Override
            public void moved(Game game, Move move, Side mover, long nanos) {
                super.moved(game, move, mover, nanos);
                cancel.set(true);
            }
        };
        GameResult r = GameDriver.play(Position.start(), player("slow"), player("normal"), new TimeControl.MoveTime(50),
                2000, rec, cancel);
        assertEquals(1, r.game().moves().size());
        assertEquals("cancelled", r.termination()); // not "timeout"
        assertTrue(r.xNanos() >= 350_000_000L, "the slow engine took " + r.xNanos() + " ns");
    }

    @Test
    void aFlagFallsWhenAMoveTakesMoreThanTheClockHas() throws Exception {
        // 300 ms on the clock, and the slow engine needs 400 ms for its first move.
        GameResult r = GameDriver.play(Position.start(), player("slow"), player("normal"),
                new TimeControl.Clock(300, 0), GRACE, new Recorder(), new AtomicBoolean());
        assertEquals("time forfeit", r.termination());
        assertEquals(Outcome.O_WINS, r.outcome());
        assertTrue(r.detail().contains("left"), r.detail());
    }

    @Test
    void theClockIsGivenToEnginesAndIncrementsAreAdded() throws Exception {
        Recorder rec = new Recorder();
        GameResult r = GameDriver.play(Position.start(), player("normal"), player("normal"),
                new TimeControl.Clock(60_000, 500), GRACE, rec, new AtomicBoolean());
        assertEquals("game over", r.termination());
        assertEquals("X:60000:60000", rec.requested.get(0));
        // After x's first move (a few ms) it has its increment added: more than 60000 - 500 left, so about 60500 - used.
        String second = rec.requested.get(1);
        assertTrue(second.startsWith("O:") && second.endsWith(":60000"), second);
        long xAfter = Long.parseLong(second.split(":")[1]);
        assertTrue(xAfter > 60_000 - 200 && xAfter <= 60_500, "x has " + xAfter);
    }

    @Test
    void cancellingAbandonsTheGameAndLeavesTheEnginesUsable() throws Exception {
        EnginePlayer x = player("slow");
        EnginePlayer o = player("slow");
        AtomicBoolean cancel = new AtomicBoolean();
        Thread canceller = new Thread(() -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            cancel.set(true);
        });
        canceller.start();
        GameResult r = GameDriver.play(Position.start(), x, o, new TimeControl.MoveTime(1000), GRACE, new Recorder(), cancel);
        assertEquals("cancelled", r.termination());
        assertEquals(Outcome.ONGOING, r.outcome());
        assertFalse(r.finished());
        assertTrue(x.isHealthy());
        x.newGame(); // waits for the stopped search to end, then is ready for the next game
        o.newGame();
    }

    @Test
    void extraOptionsReplaceOrAddToTheConfiguredOnes() {
        Map<String, String> own = new java.util.LinkedHashMap<>();
        own.put("Hash", "16");
        own.put("Threads", "1");
        Map<String, String> merged = EnginePlayer.merge(own, Map.of("Hash", "64"));
        assertEquals("64", merged.get("Hash"));
        assertEquals("1", merged.get("Threads"));
        assertEquals(List.of("Hash", "Threads"), List.copyOf(merged.keySet()));
        assertEquals(3, EnginePlayer.merge(own, Map.of("Style", "wild")).size());
    }
}
