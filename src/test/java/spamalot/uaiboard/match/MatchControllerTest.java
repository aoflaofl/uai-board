package spamalot.uaiboard.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import spamalot.uaiboard.game.Position;
import spamalot.uaiboard.game.Side;
import spamalot.uaiboard.play.EngineConfig;
import spamalot.uaiboard.play.FakeEngines;
import spamalot.uaiboard.play.TimeControl;

/** Matches between {@link spamalot.uaiboard.uai.FakeEngine}s, which play the first legal move (or misbehave on request). */
class MatchControllerTest {
    private static final TimeControl FAST = new TimeControl.MoveTime(20);

    private static MatchSetup setup(String modeA, String modeB, int games) {
        return new MatchSetup(FakeEngines.config(modeA), Map.of(), FakeEngines.config(modeB), Map.of(), FAST, games,
                Openings.random(Openings.needed(games), 4, 11), 1000);
    }

    /** Records everything that happens. */
    private static class Events implements MatchController.Listener {
        final List<String> log = Collections.synchronizedList(new ArrayList<>());
        final List<MatchGame> finished = Collections.synchronizedList(new ArrayList<>());
        final List<String> starts = Collections.synchronizedList(new ArrayList<>());
        final List<MatchController.State> states = Collections.synchronizedList(new ArrayList<>());
        volatile String message = "";
        volatile Standings last = Standings.empty();

        @Override
        public void stateChanged(MatchController.State state) {
            states.add(state);
        }

        @Override
        public void gameStarted(int index, int total, Position opening, String xName, String oName) {
            starts.add(index + "/" + total + ":" + xName + ":" + oName + ":" + opening.toFen());
        }

        @Override
        public void gameFinished(MatchGame game, Standings standings) {
            finished.add(game);
            last = standings;
        }

        @Override
        public void matchFinished(MatchController.State finalState, Standings standings, String message) {
            this.message = message;
            this.last = standings;
        }
    }

    private static void waitFor(BooleanSupplier c) throws Exception {
        for (int i = 0; i < 300; i++) {
            if (c.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("condition not reached");
    }

    @Test
    void aMatchPlaysEveryGameWithColoursSwappedOverEachOpening() throws Exception {
        Events ev = new Events();
        AtomicInteger launches = new AtomicInteger();
        MatchController m = new MatchController(setup("normal", "normal", 4), ev, (config, extra, lines) -> {
            launches.incrementAndGet();
            return EnginePlayer.launch(config, extra, lines);
        });
        m.start();
        assertTrue(m.await(60_000));
        assertEquals(MatchController.State.FINISHED, m.state());
        Standings s = m.standings();
        assertEquals(4, s.games());
        assertEquals(4, s.wins() + s.draws() + s.losses());
        assertEquals(4, ev.finished.size());
        assertEquals(List.of(0, 1, 2, 3), ev.finished.stream().map(MatchGame::index).toList());
        // Engine A is x in games 0 and 2 and o in games 1 and 3; each opening is played twice.
        assertEquals(List.of(true, false, true, false), ev.finished.stream().map(MatchGame::aIsX).toList());
        assertEquals(ev.finished.get(0).result().game().start(), ev.finished.get(1).result().game().start());
        assertEquals(ev.finished.get(2).result().game().start(), ev.finished.get(3).result().game().start());
        assertFalse(ev.finished.get(0).result().game().start().equals(ev.finished.get(2).result().game().start()));
        assertEquals(2, launches.get(), "both engines stay running between games");
        assertEquals(Map.of("game over", 4), s.terminations());
        assertTrue(s.aMoves() > 0 && s.bMoves() > 0 && s.averageMillisA() > 0 && s.averageMillisB() > 0);
        assertEquals(s, ev.last);
        assertEquals(List.of(MatchController.State.RUNNING, MatchController.State.FINISHED), ev.states);
        assertEquals(4, ev.starts.size());
        assertTrue(ev.starts.get(0).startsWith("0/4:fake-normal:fake-normal:"), ev.starts.get(0));
    }

    @Test
    void theScoreIsSeenFromEngineAAndMatchesTheEloClass() throws Exception {
        Events ev = new Events();
        MatchController m = new MatchController(setup("illegal", "normal", 4), ev);
        m.start();
        assertTrue(m.await(60_000));
        // The illegal engine A loses every game, as x at once and as o after x's first move.
        Standings s = m.standings();
        assertEquals(0, s.wins());
        assertEquals(4, s.losses());
        assertEquals(Map.of("illegal move", 4), s.terminations());
        assertEquals(Elo.of(0, 0, 4), s.elo());
        assertEquals(0.0, s.points());
        for (MatchGame g : ev.finished) {
            assertEquals(0.0, g.scoreForA());
        }
    }

    @Test
    void aCrashedEngineIsStartedAgainForTheNextGame() throws Exception {
        Events ev = new Events();
        AtomicInteger launchesOfCrasher = new AtomicInteger();
        MatchController m = new MatchController(setup("crash", "normal", 4), ev, (config, extra, lines) -> {
            if (config.name().equals("fake-crash")) {
                launchesOfCrasher.incrementAndGet();
            }
            return EnginePlayer.launch(config, extra, lines);
        });
        m.start();
        assertTrue(m.await(60_000));
        assertEquals(MatchController.State.FINISHED, m.state());
        assertEquals(4, m.standings().games());
        assertEquals(4, m.standings().losses());
        assertEquals(Map.of("engine failure", 4), m.standings().terminations());
        assertEquals(4, launchesOfCrasher.get(), "restarted before each of the four games");
    }

    @Test
    void stoppingAbandonsTheGameInProgressWithoutCountingIt() throws Exception {
        Events ev = new Events();
        MatchSetup slow = new MatchSetup(FakeEngines.config("slow"), Map.of(), FakeEngines.config("slow"), Map.of(),
                new TimeControl.MoveTime(1000), 10, Openings.random(5, 4, 3), 1000);
        MatchController m = new MatchController(slow, ev);
        m.start();
        waitFor(() -> !ev.starts.isEmpty());
        Thread.sleep(300);
        m.stop();
        assertTrue(m.await(30_000));
        assertEquals(MatchController.State.STOPPED, m.state());
        assertEquals(0, m.standings().games());
        assertTrue(m.games().isEmpty());
        assertEquals(1, ev.starts.size());
    }

    @Test
    void pausingWaitsForTheGameInProgressAndResumingContinues() throws Exception {
        java.util.concurrent.atomic.AtomicReference<MatchController> self = new java.util.concurrent.atomic.AtomicReference<>();
        Events ev = new Events() {
            @Override
            public void gameFinished(MatchGame game, Standings standings) {
                super.gameFinished(game, standings);
                if (finished.size() == 1) {
                    self.get().pause(); // asked for during the first game's end: the second game must not start
                }
            }
        };
        MatchController m = new MatchController(setup("normal", "normal", 6), ev);
        self.set(m);
        m.start();
        waitFor(() -> m.state() == MatchController.State.PAUSED);
        assertEquals(1, m.standings().games());
        assertEquals(1, ev.starts.size());
        Thread.sleep(500);
        assertEquals(1, m.standings().games(), "no game starts while paused");
        assertEquals(1, ev.starts.size());
        m.resume();
        assertTrue(m.await(60_000));
        assertEquals(6, m.standings().games());
        assertEquals(MatchController.State.FINISHED, m.state());
        assertEquals(List.of(MatchController.State.RUNNING, MatchController.State.PAUSED, MatchController.State.RUNNING,
                MatchController.State.FINISHED), ev.states);
    }

    @Test
    void aMatchCanStartPausedAndBeResumed() throws Exception {
        Events ev = new Events();
        MatchController m = new MatchController(setup("normal", "normal", 2), ev);
        m.pause();
        m.start();
        waitFor(() -> m.state() == MatchController.State.PAUSED);
        assertEquals(0, ev.starts.size());
        m.resume();
        assertTrue(m.await(60_000));
        assertEquals(2, m.standings().games());
    }

    @Test
    void stoppingWhilePausedEndsTheMatch() throws Exception {
        Events ev = new Events();
        MatchController m = new MatchController(setup("normal", "normal", 6), ev);
        m.pause();
        m.start();
        waitFor(() -> m.state() == MatchController.State.PAUSED);
        m.stop();
        assertTrue(m.await(10_000));
        assertEquals(MatchController.State.STOPPED, m.state());
        assertEquals(0, m.standings().games());
    }

    @Test
    void anEngineThatCannotStartFailsTheMatchWithItsName() throws Exception {
        Events ev = new Events();
        MatchSetup broken = new MatchSetup(new EngineConfig("ghost", "/no/such/engine"), Map.of(), FakeEngines.config("normal"),
                Map.of(), FAST, 2, Openings.random(1, 4, 1), 1000);
        MatchController m = new MatchController(broken, ev);
        m.start();
        assertTrue(m.await(30_000));
        assertEquals(MatchController.State.FAILED, m.state());
        assertTrue(ev.message.contains("cannot start ghost"), ev.message);
        assertEquals(0, m.standings().games());
    }

    @Test
    void aShortListOfOpeningsIsUsedAgainAndNamesShowMatchOnlyOptions() {
        MatchSetup s = new MatchSetup(FakeEngines.config("normal"), Map.of("Hash", "8", "Style", "wild"),
                FakeEngines.config("normal"), Map.of(), FAST, 6, Openings.random(1, 4, 5), 1000);
        assertEquals(s.openingFor(0), s.openingFor(1));
        assertEquals(s.openingFor(0), s.openingFor(5));
        assertEquals("fake-normal [Hash=8, Style=wild]", s.nameA());
        assertEquals("fake-normal", s.nameB());
        assertTrue(MatchSetup.aIsX(0) && !MatchSetup.aIsX(1) && MatchSetup.aIsX(4));
    }

    @Test
    void invalidSetupsAreRejected() {
        EngineConfig e = FakeEngines.config("normal");
        List<Position> one = List.of(Position.start());
        assertThrows(IllegalArgumentException.class, () -> new MatchSetup(e, Map.of(), e, Map.of(), FAST, 0, one, 0));
        assertThrows(IllegalArgumentException.class, () -> new MatchSetup(e, Map.of(), e, Map.of(), FAST, 1, List.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> new MatchSetup(e, Map.of(), e, Map.of(), FAST, 1, one, -1));
    }
}
