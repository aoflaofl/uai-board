package spamalot.uaiboard.match;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import spamalot.uaiboard.game.Game;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Outcome;
import spamalot.uaiboard.game.Position;
import spamalot.uaiboard.game.Side;
import spamalot.uaiboard.play.TimeControl;
import spamalot.uaiboard.uai.GoParams;
import spamalot.uaiboard.uai.UaiInfo;

/**
 * Plays one game between two engines on the calling thread: it asks the side to move for a move, checks it, and keeps the time.
 *
 * <p>Time is measured per move from sending the request to the engine's answer being read, not including any delay in this
 * thread. With a game clock the side to move is given what is left (plus the increment per move), and a move that takes longer
 * than that loses on time. With a fixed move time an engine may overrun by the grace period; beyond it, or with no answer at all,
 * it loses with a timeout. An illegal move, an engine that exits or writes an unreadable move also loses. A side that has no
 * move passes on its own, at no cost in time.
 */
public final class GameDriver {
    /** A safety net: real games end far sooner by the rules. */
    static final int MAX_PLIES = 1000;

    /** Told about the game as it goes; every method has an empty default. */
    public interface Observer {
        /** An engine is about to be asked for a move; remaining times are in ms, or -1 without a game clock. */
        default void moveRequested(Game game, Side side, long xRemainingMs, long oRemainingMs) { }

        /** An {@code info} line from the engine that is thinking (called on the engine client's reader thread). */
        default void info(Side side, UaiInfo info) { }

        /** A move was played (a pass included, with zero {@code nanos}). */
        default void moved(Game game, Move move, Side mover, long nanos) { }
    }

    private GameDriver() {}

    /**
     * Plays a game from {@code start}.
     *
     * @param graceMs how long beyond its time an engine may take before it is given up on
     * @param cancel set from another thread to abandon the game (the result then has outcome {@code ONGOING})
     */
    public static GameResult play(Position start, EnginePlayer x, EnginePlayer o, TimeControl timeControl, long graceMs,
                                  Observer observer, AtomicBoolean cancel) {
        Game game = new Game(start);
        boolean clock = timeControl instanceof TimeControl.Clock;
        long moveTimeMs = timeControl instanceof TimeControl.MoveTime t ? t.millis() : 0;
        long incrementMs = timeControl instanceof TimeControl.Clock c ? c.incrementMillis() : 0;
        long[] remaining = clock ? new long[] {((TimeControl.Clock) timeControl).baseMillis(),
                ((TimeControl.Clock) timeControl).baseMillis()} : new long[] {-1, -1};
        long[] spent = new long[2];
        int[] made = new int[2];
        x.setInfoSink(info -> observer.info(Side.X, info));
        o.setInfoSink(info -> observer.info(Side.O, info));
        try {
            while (!game.position().isGameOver()) {
                if (game.moves().size() >= MAX_PLIES) {
                    return result(game, byCount(game.position()), "ply limit", "", spent, made);
                }
                if (cancel != null && cancel.get()) {
                    return result(game, Outcome.ONGOING, "cancelled", "", spent, made);
                }
                Position pos = game.position();
                Side side = pos.sideToMove();
                int i = side == Side.X ? 0 : 1;
                EnginePlayer player = side == Side.X ? x : o;
                if (pos.legalMoves().equals(List.of(Move.pass()))) {
                    game.play(Move.pass());
                    observer.moved(game, Move.pass(), side, 0);
                    continue;
                }
                GoParams go;
                long waitMs;
                if (clock) {
                    go = GoParams.clock(Math.max(1, remaining[0]), Math.max(1, remaining[1]), incrementMs, incrementMs);
                    waitMs = Math.max(1, remaining[i]) + graceMs;
                } else {
                    go = GoParams.movetime(moveTimeMs);
                    waitMs = moveTimeMs + graceMs;
                }
                observer.moveRequested(game, side, remaining[0], remaining[1]);
                EnginePlayer.Answer answer;
                try {
                    answer = player.think(game.start(), game.moves(), go, waitMs, cancel);
                } catch (EnginePlayer.FailedException e) {
                    switch (e.failure()) {
                        case CANCELLED:
                            return result(game, Outcome.ONGOING, "cancelled", "", spent, made);
                        case NO_ANSWER:
                            return forfeit(game, side, clock ? "time forfeit" : "timeout", e.getMessage(), spent, made);
                        default:
                            return forfeit(game, side, "engine failure", player.name() + ": " + e.getMessage(), spent, made);
                    }
                }
                long usedMs = answer.nanos() / 1_000_000L;
                spent[i] += answer.nanos();
                made[i]++;
                if (clock) {
                    if (usedMs > remaining[i]) {
                        return forfeit(game, side, "time forfeit",
                                player.name() + " used " + usedMs + " ms with " + remaining[i] + " ms left", spent, made);
                    }
                    remaining[i] += incrementMs - usedMs;
                } else if (usedMs > waitMs) {
                    return forfeit(game, side, "timeout", player.name() + " took " + usedMs + " ms for a move time of "
                            + moveTimeMs + " ms", spent, made);
                }
                Move move = answer.move();
                if (!pos.isLegal(move)) {
                    return forfeit(game, side, "illegal move", player.name() + " played " + move, spent, made);
                }
                game.play(move);
                observer.moved(game, move, side, answer.nanos());
            }
            return result(game, game.position().outcome(), "game over", "", spent, made);
        } finally {
            x.setInfoSink(null);
            o.setInfoSink(null);
        }
    }

    private static Outcome byCount(Position p) {
        int cx = p.count(Side.X);
        int co = p.count(Side.O);
        return cx > co ? Outcome.X_WINS : co > cx ? Outcome.O_WINS : Outcome.DRAW;
    }

    private static GameResult forfeit(Game game, Side loser, String termination, String detail, long[] spent, int[] made) {
        return result(game, loser == Side.X ? Outcome.O_WINS : Outcome.X_WINS, termination, detail, spent, made);
    }

    private static GameResult result(Game game, Outcome outcome, String termination, String detail, long[] spent, int[] made) {
        return new GameResult(outcome, termination, detail, game, spent[0], spent[1], made[0], made[1]);
    }
}
