package spamalot.uaiboard.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Position;
import spamalot.uaiboard.uai.GoParams;
import spamalot.uaiboard.uai.UaiException;
import spamalot.uaiboard.uai.UaiInfo;

class EngineSessionTest {
    private static final class Events implements EngineSession.Callbacks {
        final BlockingQueue<Object[]> moves = new LinkedBlockingQueue<>();
        final BlockingQueue<String> problems = new LinkedBlockingQueue<>();
        final List<String> infoText = Collections.synchronizedList(new ArrayList<>());
        final List<Object> infoTokens = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void onInfo(Object token, UaiInfo info) {
            if (info.text() != null) {
                infoText.add(info.text());
            }
            infoTokens.add(token);
        }

        @Override
        public void onBestMove(Object token, Move move) {
            moves.add(new Object[] {token, move});
        }

        @Override
        public void onProblem(String message) {
            problems.add(message);
        }
    }

    @Test
    void launchSetsOptionsAndAnswersASearch() throws Exception {
        Events ev = new Events();
        try (EngineSession s = EngineSession.launch(FakeEngines.config("normal", Map.of("Hash", "8")), ev)) {
            assertEquals("FakeEngine 1.0", s.engineName());
            assertTrue(ev.infoText.contains("got setoption name Hash value 8"), ev.infoText.toString());
            s.requestMove("t1", Position.start(), List.of(), GoParams.movetime(20));
            Object[] answer = ev.moves.poll(10, TimeUnit.SECONDS);
            assertEquals("t1", answer[0]);
            assertEquals(Position.start().legalMoves().get(0), answer[1]);
        }
    }

    @Test
    void aCancelledSearchNeverReports() throws Exception {
        Events ev = new Events();
        try (EngineSession s = EngineSession.launch(FakeEngines.config("normal"), ev)) {
            s.requestMove("old", Position.start(), List.of(), GoParams.infinite());
            assertNull(ev.moves.poll(300, TimeUnit.MILLISECONDS));
            s.cancel();
            assertNull(ev.moves.poll(500, TimeUnit.MILLISECONDS));
            s.requestMove("new", Position.start(), List.of(), GoParams.movetime(20));
            assertEquals("new", ev.moves.poll(10, TimeUnit.SECONDS)[0]);
            assertTrue(ev.moves.isEmpty());
        }
    }

    @Test
    void aRequestDuringASearchReplacesIt() throws Exception {
        Events ev = new Events();
        try (EngineSession s = EngineSession.launch(FakeEngines.config("normal"), ev)) {
            s.requestMove("first", Position.start(), List.of(), GoParams.infinite());
            Thread.sleep(200);
            Position after = Position.start().makeMove(Move.parse("b6"));
            s.requestMove("second", Position.start(), List.of(Move.parse("b6")), GoParams.movetime(20));
            Object[] answer = ev.moves.poll(10, TimeUnit.SECONDS);
            assertEquals("second", answer[0]);
            assertEquals(after.legalMoves().get(0), answer[1]);
            assertNull(ev.moves.poll(500, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void anEngineThatExitsIsReported() throws Exception {
        Events ev = new Events();
        try (EngineSession s = EngineSession.launch(FakeEngines.config("crash"), ev)) {
            s.requestMove("t", Position.start(), List.of(), GoParams.movetime(20));
            String problem = ev.problems.poll(10, TimeUnit.SECONDS);
            assertTrue(problem.contains("exit code 3"), problem);
            assertEquals(false, s.isAlive());
        }
    }

    @Test
    void anUnreadableMoveIsAProblem() throws Exception {
        Events ev = new Events();
        try (EngineSession s = EngineSession.launch(FakeEngines.config("badmove"), ev)) {
            s.requestMove("t", Position.start(), List.of(), GoParams.movetime(20));
            assertTrue(ev.problems.poll(10, TimeUnit.SECONDS).contains("bestmove zz"));
        }
    }

    @Test
    void closingIsNotAProblem() throws Exception {
        Events ev = new Events();
        EngineSession s = EngineSession.launch(FakeEngines.config("normal"), ev);
        s.close();
        assertNull(ev.problems.poll(500, TimeUnit.MILLISECONDS));
    }

    @Test
    void aBrokenEngineFailsToLaunch() {
        assertThrows(UaiException.class, () -> EngineSession.launch(new EngineConfig("x", "/no/such/engine"), new Events()));
        assertThrows(UaiException.class, () -> EngineSession.launch(new EngineConfig("x", "java \"unclosed"), new Events()));
    }

    @Test
    void infoOfACancelledSearchIsDroppedAndLiveInfoCarriesItsToken() throws Exception {
        Events ev = new Events();
        try (EngineSession s = EngineSession.launch(FakeEngines.config("chatty"), ev)) {
            ev.infoTokens.clear();
            s.requestMove("a", Position.start(), List.of(), GoParams.infinite());
            for (int i = 0; i < 100 && ev.infoTokens.size() < 2; i++) {
                Thread.sleep(50);
            }
            assertTrue(ev.infoTokens.size() >= 2 && ev.infoTokens.stream().allMatch("a"::equals), ev.infoTokens.toString());
            s.cancel();
            Thread.sleep(500);
            assertTrue(ev.infoText.stream().noneMatch("late"::equals), ev.infoText.toString());
        }
    }
}
