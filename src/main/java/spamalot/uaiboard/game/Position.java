package spamalot.uaiboard.game;

import java.util.ArrayList;
import java.util.List;

/**
 * An immutable Ataxx position: the rules of the game, independent of any user interface or engine.
 *
 * <p>A clone puts a new piece on an empty square next to one of the mover's pieces; a jump moves a piece to an empty square two
 * steps away. Either way every enemy piece next to the destination is converted. A side with no move passes while the game
 * goes on. The game ends when a side has no pieces, neither side can move, or 100 half-moves have passed without a clone;
 * the side with more pieces then wins.
 */
public final class Position {
    public static final String START_FEN = "x5o/7/7/7/7/7/o5x x 0 1";
    /** Half-moves without a clone after which the game ends. */
    public static final int HALFMOVE_LIMIT = 100;

    private final long x;
    private final long o;
    private final long walls;
    private final Side sideToMove;
    private final int halfmoveClock;
    private final int fullmoveNumber;

    private Position(long x, long o, long walls, Side sideToMove, int halfmoveClock, int fullmoveNumber) {
        this.x = x;
        this.o = o;
        this.walls = walls;
        this.sideToMove = sideToMove;
        this.halfmoveClock = halfmoveClock;
        this.fullmoveNumber = fullmoveNumber;
    }

    public static Position start() {
        return fromFen(START_FEN);
    }

    /** Parses a FEN such as {@code x5o/7/7/7/7/7/o5x x 0 1} (rank 7 first; {@code x}, {@code o}, {@code -} for a wall). */
    public static Position fromFen(String fen) {
        String[] parts = fen.trim().split("\\s+");
        if (parts.length < 2) {
            throw new IllegalArgumentException("bad FEN: " + fen);
        }
        String[] ranks = parts[0].split("/", -1);
        if (ranks.length != Squares.SIZE) {
            throw new IllegalArgumentException("a FEN needs 7 ranks: " + fen);
        }
        long xs = 0;
        long os = 0;
        long ws = 0;
        for (int row = 0; row < Squares.SIZE; row++) {
            int rank = Squares.SIZE - 1 - row;
            int file = 0;
            for (char c : ranks[row].toCharArray()) {
                if (c >= '1' && c <= '7') {
                    file += c - '0';
                    continue;
                }
                if (file >= Squares.SIZE) {
                    throw new IllegalArgumentException("rank too long in FEN: " + fen);
                }
                long bit = 1L << Squares.of(file, rank);
                switch (c) {
                    case 'x', 'X' -> xs |= bit;
                    case 'o', 'O' -> os |= bit;
                    case '-' -> ws |= bit;
                    default -> throw new IllegalArgumentException("bad character '" + c + "' in FEN: " + fen);
                }
                file++;
            }
            if (file != Squares.SIZE) {
                throw new IllegalArgumentException("rank does not have 7 squares in FEN: " + fen);
            }
        }
        Side side = switch (parts[1]) {
            case "x", "X" -> Side.X;
            case "o", "O" -> Side.O;
            default -> throw new IllegalArgumentException("bad side to move in FEN: " + fen);
        };
        int halfmove = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        int fullmove = parts.length > 3 ? Integer.parseInt(parts[3]) : 1;
        return new Position(xs, os, ws, side, halfmove, fullmove);
    }

    public String toFen() {
        StringBuilder sb = new StringBuilder();
        for (int rank = Squares.SIZE - 1; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < Squares.SIZE; file++) {
                Piece p = pieceAt(Squares.of(file, rank));
                if (p == Piece.EMPTY) {
                    empty++;
                    continue;
                }
                if (empty > 0) {
                    sb.append(empty);
                    empty = 0;
                }
                sb.append(p.letter());
            }
            if (empty > 0) {
                sb.append(empty);
            }
            if (rank > 0) {
                sb.append('/');
            }
        }
        return sb.append(' ').append(sideToMove.letter()).append(' ').append(halfmoveClock).append(' ')
                .append(fullmoveNumber).toString();
    }

    /** What is on a square. */
    public enum Piece {
        EMPTY('.'),
        X('x'),
        O('o'),
        WALL('-');

        private final char letter;

        Piece(char letter) {
            this.letter = letter;
        }

        public char letter() {
            return letter;
        }
    }

    public Piece pieceAt(int square) {
        long bit = 1L << square;
        return (x & bit) != 0 ? Piece.X : (o & bit) != 0 ? Piece.O : (walls & bit) != 0 ? Piece.WALL : Piece.EMPTY;
    }

    public Side sideToMove() {
        return sideToMove;
    }

    public int halfmoveClock() {
        return halfmoveClock;
    }

    public int fullmoveNumber() {
        return fullmoveNumber;
    }

    private long pieces(Side side) {
        return side == Side.X ? x : o;
    }

    public int count(Side side) {
        return Long.bitCount(pieces(side));
    }

    private long empty() {
        return ~(x | o | walls) & ((1L << Squares.COUNT) - 1);
    }

    /** The squares within {@code reach} steps of {@code square}, itself excluded. */
    private static long around(int square, int reach) {
        long mask = 0;
        for (int s = 0; s < Squares.COUNT; s++) {
            int d = Squares.distance(square, s);
            if (d >= 1 && d <= reach) {
                mask |= 1L << s;
            }
        }
        return mask;
    }

    private static final long[] NEIGHBOURS = new long[Squares.COUNT];
    private static final long[] WITHIN_TWO = new long[Squares.COUNT];

    static {
        for (int s = 0; s < Squares.COUNT; s++) {
            NEIGHBOURS[s] = around(s, 1);
            WITHIN_TWO[s] = around(s, 2);
        }
    }

    /** True if {@code side} has a real (non-pass) move. */
    private boolean hasMove(Side side) {
        long empty = empty();
        for (long p = pieces(side); p != 0; p &= p - 1) {
            if ((WITHIN_TWO[Long.numberOfTrailingZeros(p)] & empty) != 0) {
                return true;
            }
        }
        return false;
    }

    public boolean isGameOver() {
        return x == 0 || o == 0 || halfmoveClock >= HALFMOVE_LIMIT || (!hasMove(Side.X) && !hasMove(Side.O));
    }

    public Outcome outcome() {
        if (!isGameOver()) {
            return Outcome.ONGOING;
        }
        int cx = count(Side.X);
        int co = count(Side.O);
        return cx > co ? Outcome.X_WINS : co > cx ? Outcome.O_WINS : Outcome.DRAW;
    }

    /**
     * The legal moves: clones, then jumps; a lone pass if the side to move is stuck but the game goes on; none once the
     * game is over.
     */
    public List<Move> legalMoves() {
        List<Move> moves = new ArrayList<>();
        if (isGameOver()) {
            return moves;
        }
        long empty = empty();
        long mine = pieces(sideToMove);
        long cloneTargets = 0;
        for (long p = mine; p != 0; p &= p - 1) {
            cloneTargets |= NEIGHBOURS[Long.numberOfTrailingZeros(p)];
        }
        cloneTargets &= empty;
        for (long t = cloneTargets; t != 0; t &= t - 1) {
            moves.add(Move.clone(Long.numberOfTrailingZeros(t)));
        }
        for (long p = mine; p != 0; p &= p - 1) {
            int from = Long.numberOfTrailingZeros(p);
            long jumpTargets = WITHIN_TWO[from] & ~NEIGHBOURS[from] & empty;
            for (long t = jumpTargets; t != 0; t &= t - 1) {
                moves.add(Move.jump(from, Long.numberOfTrailingZeros(t)));
            }
        }
        if (moves.isEmpty()) {
            moves.add(Move.pass());
        }
        return moves;
    }

    public boolean isLegal(Move move) {
        return legalMoves().contains(move);
    }

    /**
     * The moves the piece on {@code from} can make, for a user interface that lets the player pick a piece first: the clones to
     * its empty neighbours, then its jumps. Empty if the square does not hold a piece of the side to move.
     */
    public List<Move> movesFrom(int from) {
        List<Move> out = new ArrayList<>();
        if (pieceAt(from) != (sideToMove == Side.X ? Piece.X : Piece.O)) {
            return out;
        }
        List<Move> legal = legalMoves();
        for (Move m : legal) {
            if (m.isClone() && Squares.distance(from, m.to()) == 1) {
                out.add(m);
            }
        }
        for (Move m : legal) {
            if (m.isJump() && m.from() == from) {
                out.add(m);
            }
        }
        return out;
    }

    /** The position after {@code move}; throws if it is not legal here. */
    public Position makeMove(Move move) {
        if (!isLegal(move)) {
            throw new IllegalArgumentException("illegal move " + move + " in " + toFen());
        }
        long mine = pieces(sideToMove);
        long theirs = pieces(sideToMove.opponent());
        int halfmove;
        if (move.isPass()) {
            halfmove = halfmoveClock + 1;
        } else {
            if (move.isClone()) {
                halfmove = 0;
            } else {
                mine &= ~(1L << move.from());
                halfmove = halfmoveClock + 1;
            }
            mine |= 1L << move.to();
            long captured = NEIGHBOURS[move.to()] & theirs;
            mine |= captured;
            theirs &= ~captured;
        }
        int fullmove = sideToMove == Side.O ? fullmoveNumber + 1 : fullmoveNumber;
        return sideToMove == Side.X
                ? new Position(mine, theirs, walls, Side.O, halfmove, fullmove)
                : new Position(theirs, mine, walls, Side.X, halfmove, fullmove);
    }

    /** Number of enemy pieces {@code move} would convert. */
    public int conversions(Move move) {
        if (move.isPass()) {
            return 0;
        }
        return Long.bitCount(NEIGHBOURS[move.to()] & pieces(sideToMove.opponent()));
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Position p && x == p.x && o == p.o && walls == p.walls && sideToMove == p.sideToMove
                && halfmoveClock == p.halfmoveClock && fullmoveNumber == p.fullmoveNumber;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(x, o, walls, sideToMove, halfmoveClock, fullmoveNumber);
    }

    @Override
    public String toString() {
        return toFen();
    }
}
