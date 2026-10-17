package spamalot.uaiboard.uai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Position;

/** Runs the client against {@link FakeEngine} in a separate JVM. */
class UaiClientTest {
    /** Collects everything the listener is told. */
    private static final class Recorder implements UaiClient.Listener {
        final BlockingQueue<Move> bestMoves = new LinkedBlockingQueue<>();
        final List<UaiInfo> infos = Collections.synchronizedList(new ArrayList<>());
        final List<String> sent = Collections.synchronizedList(new ArrayList<>());
        final List<String> errors = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch exited = new CountDownLatch(1);
        volatile int exitCode = Integer.MIN_VALUE;

        @Override
        public void onInfo(UaiInfo info) {
            infos.add(info);
        }

        @Override
        public void onBestMove(Move move) {
            bestMoves.add(move);
        }

        @Override
        public void onLine(UaiClient.Direction direction, String line) {
            if (direction == UaiClient.Direction.SENT) {
                sent.add(line);
            }
        }

        @Override
        public void onProtocolError(String message) {
            errors.add(message);
        }

        @Override
        public void onExit(int code) {
            exitCode = code;
            exited.countDown();
        }
    }

    private static List<Move> moves(String... text) {
        return java.util.Arrays.stream(text).map(Move::parse).toList();
    }

    private static List<String> fake(String mode) {
        return List.of(System.getProperty("java.home") + "/bin/java", "-cp", System.getProperty("java.class.path"),
                FakeEngine.class.getName(), mode);
    }

    private static UaiClient start(String mode, Recorder rec) throws UaiException {
        return UaiClient.start(fake(mode), rec);
    }

    private static List<String> infoTexts(Recorder rec) {
        synchronized (rec.infos) {
            return rec.infos.stream().map(UaiInfo::text).filter(t -> t != null).toList();
        }
    }

    private static void awaitInfoText(Recorder rec, String prefix) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (infoTexts(rec).stream().anyMatch(t -> t.startsWith(prefix))) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("never saw info string " + prefix + ": " + infoTexts(rec));
    }

    @Test
    void handshakeReadsIdAndOptions() throws Exception {
        Recorder rec = new Recorder();
        try (UaiClient c = start("normal", rec)) {
            UaiClient.EngineId id = c.handshake(Duration.ofSeconds(10));
            assertEquals("FakeEngine 1.0", id.name());
            assertEquals("Test Author", id.author());
            assertEquals(3, id.options().size());
            assertEquals("Hash", id.options().get(0).name());
            assertEquals("Clear Hash", id.options().get(2).name());
            c.waitReady(Duration.ofSeconds(10));
            assertTrue(c.isAlive());
        }
    }

    @Test
    void optionsAndNewGameWaitForReady() throws Exception {
        Recorder rec = new Recorder();
        try (UaiClient c = start("normal", rec)) {
            c.handshake(Duration.ofSeconds(10));
            c.setOption("Hash", "64");
            awaitInfoText(rec, "got setoption name Hash value 64");
            c.newGame();
            assertTrue(rec.sent.contains("uainewgame"));
        }
    }

    @Test
    void searchDeliversInfoAndBestMove() throws Exception {
        Recorder rec = new Recorder();
        try (UaiClient c = start("normal", rec)) {
            c.handshake(Duration.ofSeconds(10));
            c.setPosition(Position.start(), List.of());
            c.go(GoParams.movetime(50));
            Move best = rec.bestMoves.poll(10, TimeUnit.SECONDS);
            assertEquals(Position.start().legalMoves().get(0), best);
            assertTrue(rec.sent.contains("go movetime 50"));
            UaiInfo mate = rec.infos.stream().filter(i -> i.score() != null && i.score().mate()).findFirst().orElseThrow();
            assertEquals(-2, mate.score().value());
            assertEquals(2, mate.depth());
            assertEquals(5000L, mate.nps());
        }
    }

    @Test
    void stopEndsAnInfiniteSearch() throws Exception {
        Recorder rec = new Recorder();
        try (UaiClient c = start("normal", rec)) {
            c.handshake(Duration.ofSeconds(10));
            c.setPosition(Position.start(), List.of());
            c.go(GoParams.infinite());
            assertEquals(null, rec.bestMoves.poll(300, TimeUnit.MILLISECONDS));
            c.stop();
            assertTrue(rec.bestMoves.poll(10, TimeUnit.SECONDS) != null);
        }
    }

    @Test
    void positionCommandsUseStartposFenOrBareFen() throws Exception {
        Recorder rec = new Recorder();
        try (UaiClient c = start("normal", rec)) {
            c.handshake(Duration.ofSeconds(10));
            assertEquals("position startpos", c.positionCommand(Position.start(), List.of()));
            assertEquals("position startpos moves b6 b2", c.positionCommand(Position.start(), moves("b6", "b2")));
            Position other = Position.fromFen("x5o/7/7/7/7/7/o5x o 0 1");
            assertEquals("position fen x5o/7/7/7/7/7/o5x o 0 1 moves b2", c.positionCommand(other, moves("b2")));
            c.setFenOnly(true);
            assertEquals("position fen x5o/7/7/7/7/7/o5x o 0 1", c.positionCommand(other, List.of()));
            String after = c.positionCommand(Position.start(), moves("b6"));
            assertTrue(after.startsWith("position fen ") && !after.contains("moves"), after);
            // The engine really accepts a move list.
            c.setFenOnly(false);
            c.setPosition(Position.start(), moves("b6", "b2"));
            awaitInfoText(rec, "got position startpos moves b6 b2");
        }
    }

    @Test
    void unreadableBestMoveIsReported() throws Exception {
        Recorder rec = new Recorder();
        try (UaiClient c = start("badmove", rec)) {
            c.handshake(Duration.ofSeconds(10));
            c.go(GoParams.movetime(10));
            for (int i = 0; i < 100 && rec.errors.isEmpty(); i++) {
                Thread.sleep(50);
            }
            assertEquals(1, rec.errors.size());
            assertTrue(rec.errors.get(0).contains("bestmove zz"));
            assertTrue(rec.bestMoves.isEmpty());
        }
    }

    @Test
    void aSilentEngineTimesOutTheHandshake() throws Exception {
        try (UaiClient c = start("silent", new Recorder())) {
            UaiException e = assertThrows(UaiException.class, () -> c.handshake(Duration.ofMillis(500)));
            assertTrue(e.getMessage().contains("uaiok"), e.getMessage());
        }
    }

    @Test
    void aSlowReadyTimesOut() throws Exception {
        try (UaiClient c = start("slowready", new Recorder())) {
            c.handshake(Duration.ofSeconds(10));
            assertThrows(UaiException.class, () -> c.waitReady(Duration.ofMillis(300)));
        }
    }

    @Test
    void anEngineThatExitsIsNoticed() throws Exception {
        Recorder rec = new Recorder();
        try (UaiClient c = start("crash", rec)) {
            c.handshake(Duration.ofSeconds(10));
            c.go(GoParams.movetime(10));
            assertTrue(rec.exited.await(10, TimeUnit.SECONDS));
            assertEquals(3, rec.exitCode);
            assertFalse(c.isAlive());
            assertThrows(UaiException.class, c::stop);
            assertThrows(UaiException.class, () -> c.waitReady(Duration.ofSeconds(1)));
        }
    }

    @Test
    void aMissingProgramIsAnError() {
        UaiException e = assertThrows(UaiException.class,
                () -> UaiClient.start(List.of("/no/such/engine"), new Recorder()));
        assertTrue(e.getMessage().contains("/no/such/engine"));
        assertThrows(UaiException.class, () -> UaiClient.start(List.of(), new Recorder()));
    }

    @Test
    void closeIsIdempotentAndKillsTheProcess() throws Exception {
        UaiClient c = start("normal", new Recorder());
        c.handshake(Duration.ofSeconds(10));
        c.close();
        c.close();
        assertFalse(c.isAlive());
    }
}
