package spamalot.uaiboard.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Position;
import spamalot.uaiboard.game.Side;
import spamalot.uaiboard.uai.UaiClient;
import spamalot.uaiboard.uai.UaiInfo;

/** The analyser against {@link spamalot.uaiboard.uai.FakeEngine}, with one thread standing in for the event dispatch thread. */
class AnalyserTest {
    private record Seen(UaiInfo info, Side side) { }

    private final ExecutorService ui = Executors.newSingleThreadExecutor();
    private final List<Seen> infos = Collections.synchronizedList(new ArrayList<>());
    private final List<String> statuses = Collections.synchronizedList(new ArrayList<>());
    private final List<String> problems = Collections.synchronizedList(new ArrayList<>());
    private Analyser analyser;

    @BeforeEach
    void setUp() throws Exception {
        analyser = ui.submit(() -> new Analyser(ui, new Analyser.Listener() {
            @Override
            public void status(String text) {
                statuses.add(text);
            }

            @Override
            public void info(UaiInfo info, Side sideToMove) {
                infos.add(new Seen(info, sideToMove));
            }

            @Override
            public void problem(String message) {
                problems.add(message);
            }

            @Override
            public void line(UaiClient.Direction direction, String line) { }
        })).get();
    }

    @AfterEach
    void tearDown() throws Exception {
        ui.submit(analyser::close).get();
        ui.shutdownNow();
    }

    private void run(Runnable r) throws Exception {
        ui.submit(r).get();
    }

    private void await(BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 200; i++) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("condition not reached; statuses " + statuses + " problems " + problems);
    }

    @Test
    void analysesThePositionAndFollowsItWhenItChanges() throws Exception {
        run(() -> analyser.analyse(Position.start(), List.of()));
        run(() -> analyser.start(FakeEngines.config("normal")));
        await(() -> infos.size() >= 2);
        assertEquals(Side.X, infos.get(0).side());
        assertTrue(statuses.stream().anyMatch(s -> s.contains("is analysing")), statuses.toString());
        assertTrue(get(analyser::isRunning));

        infos.clear();
        run(() -> analyser.analyse(Position.start(), List.of(Move.parse("b6"))));
        await(() -> searchLines().size() >= 2);
        assertEquals(Side.O, searchLines().get(0).side());
        // The principal variation is the engine's first legal move in the new position.
        Position after = Position.start().makeMove(Move.parse("b6"));
        assertEquals(List.of(after.legalMoves().get(0)), searchLines().get(0).info().pv());
        assertTrue(problems.isEmpty(), problems.toString());
    }

    /** The lines that report a search, without the engine's echo of the commands it was sent. */
    private List<Seen> searchLines() {
        synchronized (infos) {
            return infos.stream().filter(i -> i.info().depth() != null).toList();
        }
    }

    private <T> T get(java.util.function.Supplier<T> s) throws Exception {
        return ui.submit(s::get).get();
    }

    @Test
    void aFinishedGameHasNothingToAnalyse() throws Exception {
        run(() -> analyser.start(FakeEngines.config("normal")));
        Position over = Position.fromFen("xxxxxxx/xxxxxxx/xxxxxxx/xxxxxxx/xxxxxxx/xxxxxxx/xxxxxxo x 0 1");
        run(() -> analyser.analyse(over, List.of()));
        await(() -> statuses.stream().anyMatch(s -> s.contains("nothing to analyse")));
        Thread.sleep(300);
        assertTrue(infos.isEmpty());
    }

    @Test
    void stoppingEndsTheAnalysisQuietly() throws Exception {
        run(() -> analyser.analyse(Position.start(), List.of()));
        run(() -> analyser.start(FakeEngines.config("normal")));
        await(() -> infos.size() >= 2);
        run(analyser::stop);
        assertFalse(get(analyser::isRunning));
        infos.clear();
        run(() -> analyser.analyse(Position.start(), List.of(Move.parse("b6"))));
        Thread.sleep(500);
        assertTrue(infos.isEmpty(), "no engine runs after stop");
        assertTrue(problems.isEmpty());
    }

    @Test
    void anEngineThatFailsToStartIsReported() throws Exception {
        run(() -> analyser.start(new EngineConfig("nope", "/no/such/engine")));
        await(() -> !problems.isEmpty());
        assertTrue(problems.get(0).contains("/no/such/engine"), problems.toString());
        assertFalse(get(analyser::isRunning));
    }

    @Test
    void anEngineThatDiesIsReportedAndAnalysisStops() throws Exception {
        run(() -> analyser.analyse(Position.start(), List.of()));
        run(() -> analyser.start(FakeEngines.config("crash")));
        await(() -> !problems.isEmpty());
        assertFalse(get(analyser::isRunning));
    }

    @Test
    void switchingEnginesReplacesTheOldOne() throws Exception {
        run(() -> analyser.analyse(Position.start(), List.of()));
        run(() -> analyser.start(FakeEngines.config("normal")));
        await(() -> infos.size() >= 2);
        infos.clear();
        run(() -> analyser.start(FakeEngines.config("normal")));
        await(() -> infos.size() >= 2);
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void anEngineThatIgnoresStopIsRestartedForEachPosition() throws Exception {
        run(() -> analyser.analyse(Position.start(), List.of()));
        run(() -> analyser.start(FakeEngines.config("deaf")));
        await(() -> searchLines().size() >= 2);
        assertEquals(Side.X, searchLines().get(0).side());

        // The first change waits for the old search to end, notices it does not, and restarts the engine.
        infos.clear();
        run(() -> analyser.analyse(Position.start(), List.of(Move.parse("b6"))));
        await(() -> searchLines().size() >= 2);
        assertEquals(Side.O, searchLines().get(0).side());
        assertTrue(searchLines().stream().allMatch(i -> i.side() == Side.O));

        // From then on a change restarts at once.
        infos.clear();
        run(() -> analyser.analyse(Position.start(), List.of(Move.parse("b6"), Move.parse("b2"))));
        await(() -> searchLines().size() >= 2);
        assertTrue(searchLines().stream().allMatch(i -> i.side() == Side.X));
        assertTrue(problems.isEmpty(), problems.toString());
        assertTrue(get(analyser::isRunning));
    }
}
