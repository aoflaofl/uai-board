package spamalot.uaiboard.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Outcome;
import spamalot.uaiboard.game.Side;
import spamalot.uaiboard.uai.UaiClient;
import spamalot.uaiboard.uai.UaiInfo;

/** The controller against {@link spamalot.uaiboard.uai.FakeEngine}, with one thread standing in for the event dispatch thread. */
class PlayControllerTest {
    private final ExecutorService ui = Executors.newSingleThreadExecutor();
    private final AtomicLong now = new AtomicLong();
    private final List<String> sent = Collections.synchronizedList(new ArrayList<>());
    private final List<String> problems = Collections.synchronizedList(new ArrayList<>());
    private final List<Outcome> gameOvers = Collections.synchronizedList(new ArrayList<>());
    private final List<String> gameOverReasons = Collections.synchronizedList(new ArrayList<>());
    private volatile int infoCount;
    private PlayController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = ui.submit(() -> new PlayController(ui, new PlayController.View() {
            @Override
            public void positionChanged() { }

            @Override
            public void engineStatus(String text) { }

            @Override
            public void info(UaiInfo info) {
                infoCount++;
            }

            @Override
            public void gameOver(Outcome outcome, String reason) {
                gameOvers.add(outcome);
                gameOverReasons.add(reason);
            }

            @Override
            public void problem(String message) {
                problems.add(message);
            }

            @Override
            public void engineLine(UaiClient.Direction direction, String line) {
                if (direction == UaiClient.Direction.SENT) {
                    sent.add(line);
                }
            }
        }, now::get)).get();
    }

    @AfterEach
    void tearDown() throws Exception {
        ui.submit(controller::close).get();
        ui.shutdownNow();
    }

    private void run(Runnable r) throws Exception {
        try {
            ui.submit(r).get();
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception ex ? ex : e;
        }
    }

    private <T> T get(Supplier<T> s) throws Exception {
        return ui.submit(s::get).get();
    }

    private void await(BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 200; i++) {
            if (get(condition::getAsBoolean)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("condition not reached");
    }

    private static GameSetup vsFake(Side human) {
        return new GameSetup("fake", human, new TimeControl.MoveTime(20));
    }

    @Test
    void twoPeopleShareTheBoard() throws Exception {
        run(() -> controller.startGame(new GameSetup(null, Side.X, GameSetup.DEFAULT.timeControl()), null));
        assertTrue(get(controller::inputAllowed));
        run(() -> controller.humanMove(Move.parse("b6")));
        assertEquals(1, get(() -> controller.game().moves().size()));
        assertTrue(get(controller::inputAllowed)); // o's turn, still a person
        run(() -> controller.humanMove(Move.parse("b2")));
        assertEquals(2, get(() -> controller.game().moves().size()));
    }

    @Test
    void anEngineAnswersAHumanMove() throws Exception {
        run(() -> controller.startGame(vsFake(Side.X), FakeEngines.config("normal")));
        assertFalse(get(controller::inputAllowed)); // not ready yet
        await(controller::inputAllowed);
        run(() -> controller.humanMove(Move.parse("b6")));
        await(() -> controller.game().moves().size() == 2);
        assertTrue(get(controller::inputAllowed));
        assertEquals(Side.X, get(() -> controller.game().position().sideToMove()));
        assertTrue(infoCount > 0);
    }

    @Test
    void theEngineMovesFirstWhenThePersonPlaysO() throws Exception {
        run(() -> controller.startGame(vsFake(Side.O), FakeEngines.config("normal")));
        await(() -> controller.game().moves().size() == 1);
        await(controller::inputAllowed);
        assertEquals(Side.O, get(() -> controller.game().position().sideToMove()));
    }

    @Test
    void undoGoesBackToThePersonsTurn() throws Exception {
        run(() -> controller.startGame(vsFake(Side.X), FakeEngines.config("normal")));
        await(controller::inputAllowed);
        run(() -> controller.humanMove(Move.parse("b6")));
        await(() -> controller.game().moves().size() == 2);
        run(controller::undo);
        assertEquals(0, get(() -> controller.game().moves().size()));
        assertTrue(get(controller::inputAllowed));
        run(() -> controller.humanMove(Move.parse("f1")));
        await(() -> controller.game().moves().size() == 2);
    }

    @Test
    void undoWhileTheEngineThinksIgnoresItsLateAnswer() throws Exception {
        run(() -> controller.startGame(vsFake(Side.X), FakeEngines.config("slow")));
        await(controller::inputAllowed);
        run(() -> controller.humanMove(Move.parse("b6")));
        Thread.sleep(100); // the engine is now thinking for 400 ms
        run(controller::undo);
        assertEquals(0, get(() -> controller.game().moves().size()));
        Thread.sleep(800); // its old answer arrives and must be dropped
        assertEquals(0, get(() -> controller.game().moves().size()));
        assertTrue(get(controller::inputAllowed));
        run(() -> controller.humanMove(Move.parse("f1")));
        await(() -> controller.game().moves().size() == 2);
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void aCrashingEngineLeavesATwoPersonGame() throws Exception {
        run(() -> controller.startGame(vsFake(Side.X), FakeEngines.config("crash")));
        await(controller::inputAllowed);
        run(() -> controller.humanMove(Move.parse("b6")));
        await(() -> !problems.isEmpty());
        assertTrue(problems.get(0).contains("exit"), problems.toString());
        assertTrue(get(controller::inputAllowed)); // o to move, now played by a person
        run(() -> controller.humanMove(Move.parse("b2")));
        assertEquals(2, get(() -> controller.game().moves().size()));
    }

    @Test
    void anEngineThatCannotStartIsAProblemNotAHang() throws Exception {
        run(() -> controller.startGame(vsFake(Side.X), new EngineConfig("nope", "/no/such/engine")));
        await(() -> !problems.isEmpty());
        assertTrue(problems.get(0).contains("/no/such/engine"), problems.toString());
        assertTrue(get(controller::inputAllowed));
    }

    @Test
    void anIllegalEngineMoveLosesTheGame() throws Exception {
        run(() -> controller.startGame(vsFake(Side.X), FakeEngines.config("illegal")));
        await(controller::inputAllowed);
        run(() -> controller.humanMove(Move.parse("b6")));
        await(() -> !gameOvers.isEmpty());
        assertEquals(Outcome.X_WINS, gameOvers.get(0));
        assertTrue(gameOverReasons.get(0).contains("illegal move"), gameOverReasons.toString());
        assertTrue(get(controller::isOver));
        assertFalse(get(controller::inputAllowed));
    }

    @Test
    void anEngineThatNeverAnswersIsGivenUpOn() throws Exception {
        run(() -> controller.setAnswerGraceMs(300));
        run(() -> controller.startGame(vsFake(Side.X), FakeEngines.config("mute")));
        await(controller::inputAllowed);
        run(() -> controller.humanMove(Move.parse("b6")));
        await(() -> !problems.isEmpty());
        assertTrue(problems.get(0).contains("did not answer"), problems.toString());
    }

    @Test
    void theClockGivesTheEngineItsTimeAndCallsAFlag() throws Exception {
        run(() -> controller.startGame(new GameSetup("fake", Side.X, new TimeControl.Clock(60_000, 1000)),
                FakeEngines.config("normal")));
        await(controller::inputAllowed);
        assertEquals(Side.X, get(controller::clockRunning));
        now.addAndGet(5_000L * 1_000_000L); // the person thinks for 5 s
        run(() -> controller.humanMove(Move.parse("b6")));
        await(() -> controller.game().moves().size() == 2);
        String go = sent.stream().filter(l -> l.startsWith("go ")).findFirst().orElseThrow();
        assertEquals("go wtime 60000 btime 56000 winc 1000 binc 1000", go);
        assertEquals(56_000, get(() -> controller.clockMillis(Side.X)));
        // Now the person runs out of time.
        now.addAndGet(100_000L * 1_000_000L);
        run(controller::tick);
        assertEquals(List.of(Outcome.O_WINS), gameOvers);
        assertEquals("x lost on time", gameOverReasons.get(0));
        assertFalse(get(controller::inputAllowed));
        assertEquals(Outcome.O_WINS, get(controller::outcome));
    }

    @Test
    void startingANewGameDropsTheOldOne() throws Exception {
        run(() -> controller.startGame(vsFake(Side.X), FakeEngines.config("normal")));
        run(() -> controller.startGame(vsFake(Side.O), FakeEngines.config("normal")));
        await(() -> controller.game().moves().size() == 1);
        await(controller::inputAllowed);
        assertEquals(1, get(() -> controller.game().moves().size()));
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void aPersonCanResignAgainstTheEngine() throws Exception {
        run(() -> controller.startGame(vsFake(Side.X), FakeEngines.config("normal")));
        await(controller::inputAllowed);
        assertTrue(get(controller::canResign));
        assertFalse(get(controller::canAgreeDraw)); // not against an engine
        run(controller::resign);
        assertEquals(List.of(Outcome.O_WINS), gameOvers);
        assertEquals("x resigned", gameOverReasons.get(0));
        assertTrue(get(controller::isOver));
        assertFalse(get(controller::canResign));
        assertEquals("FakeEngine 1.0", get(controller::opponentName));
    }

    @Test
    void betweenTwoPeopleTheSideToMoveResignsOrBothAgreeToADraw() throws Exception {
        GameSetup hotSeat = new GameSetup(null, Side.X, GameSetup.DEFAULT.timeControl());
        run(() -> controller.startGame(hotSeat, null));
        assertTrue(get(controller::canAgreeDraw));
        run(() -> controller.humanMove(Move.parse("b6")));
        run(controller::resign); // o is to move
        assertEquals(List.of(Outcome.X_WINS), gameOvers);
        assertEquals("o resigned", gameOverReasons.get(0));

        run(() -> controller.startGame(hotSeat, null));
        run(controller::agreeDraw);
        assertEquals(Outcome.DRAW, gameOvers.get(1));
        assertEquals("draw agreed", gameOverReasons.get(1));
        assertEquals(null, get(controller::opponentName));
    }

    @Test
    void aLoadedGameCanBePlayedOn() throws Exception {
        spamalot.uaiboard.game.Game g = new spamalot.uaiboard.game.Game();
        g.play(Move.parse("b6"));
        g.play(Move.parse("b2"));
        run(() -> controller.startGame(vsFake(Side.X), FakeEngines.config("normal")));
        run(() -> controller.loadGame(g));
        assertEquals(2, get(() -> controller.game().moves().size()));
        assertTrue(get(controller::inputAllowed)); // hot seat, x to move
        run(() -> controller.humanMove(Move.parse("a7c5")));
        assertEquals(3, get(() -> controller.game().moves().size()));
        run(controller::undo);
        run(controller::undo);
        assertEquals(1, get(() -> controller.game().moves().size()));
    }

    @Test
    void aGameCanStartFromAnyPosition() throws Exception {
        // o to move and the person is x: the engine moves first.
        spamalot.uaiboard.game.Position start = spamalot.uaiboard.game.Position.fromFen("x5o/7/7/7/7/7/o5x o 0 5");
        run(() -> controller.startGame(vsFake(Side.X), FakeEngines.config("normal"), start));
        await(() -> controller.game().moves().size() == 1);
        await(controller::inputAllowed);
        assertEquals(start, get(() -> controller.game().start()));
    }

    @Test
    void aStuckSideToMovePassesAtTheStart() throws Exception {
        // o is boxed in and to move: the game starts with a pass, and it is x's turn.
        spamalot.uaiboard.game.Position start =
                spamalot.uaiboard.game.Position.fromFen("7/7/7/7/---4/--x4/o-x4 o 0 1");
        run(() -> controller.startGame(new GameSetup(null, Side.X, GameSetup.DEFAULT.timeControl()), null, start));
        assertEquals(List.of(Move.pass()), get(() -> controller.game().moves()));
        assertEquals(Side.X, get(() -> controller.game().position().sideToMove()));
        assertTrue(get(controller::inputAllowed));
    }

    @Test
    void undoNeverLeavesAStuckSideToMove() throws Exception {
        // o is boxed in: the game starts with o's pass, x plays d2, and o passes again.
        spamalot.uaiboard.game.Position start =
                spamalot.uaiboard.game.Position.fromFen("7/7/7/7/---4/--x4/o-x4 o 0 1");
        run(() -> controller.startGame(new GameSetup(null, Side.X, GameSetup.DEFAULT.timeControl()), null, start));
        run(() -> controller.humanMove(Move.parse("d2")));
        assertEquals(List.of(Move.pass(), Move.parse("d2"), Move.pass()), get(() -> controller.game().moves()));
        run(controller::undo); // takes back d2 and the pass that followed it
        assertEquals(List.of(Move.pass()), get(() -> controller.game().moves()));
        assertEquals(Side.X, get(() -> controller.game().position().sideToMove()));
        assertTrue(get(controller::inputAllowed));
        run(controller::undo); // back to the start: the stuck side passes again by itself
        assertEquals(List.of(Move.pass()), get(() -> controller.game().moves()));
        assertEquals(Side.X, get(() -> controller.game().position().sideToMove()));
    }
}
