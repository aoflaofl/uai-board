package spamalot.uaiboard.game;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A finished or unfinished game as text: a few tags, then the moves, in a form like PGN. Example:
 *
 * <pre>
 * [Event "uai-board game"]
 * [Date "2026.10.17"]
 * [X "You"]
 * [O "gataxx 1.9.0"]
 * [Result "1-0"]
 *
 * 1. b6 b2 2. a7c5 f6
 * </pre>
 *
 * A {@code [FEN "..."]} tag gives a start position other than the standard one. Result is {@code 1-0} (x won), {@code 0-1} (o
 * won), {@code 1/2-1/2} or {@code *} (not finished). Reading accepts move numbers, comments in braces and a result at the end
 * of the moves, and checks that every move is legal.
 */
public record GameRecord(Map<String, String> tags, Position start, List<Move> moves) {
    private static final Pattern TAG = Pattern.compile("^\\s*\\[(\\w+)\\s+\"(.*)\"\\s*\\]\\s*$");
    private static final Pattern MOVE_NUMBER = Pattern.compile("^\\d+\\.(\\.\\.)?$");
    private static final List<String> RESULTS = List.of("1-0", "0-1", "1/2-1/2", "*");

    public GameRecord {
        tags = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(tags));
        moves = List.copyOf(moves);
    }

    /** The record of {@code game} with the given names and result tag ({@code 1-0}, {@code 0-1}, {@code 1/2-1/2} or {@code *}). */
    public static GameRecord of(Game game, String xName, String oName, String result, LocalDate date) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("Event", "uai-board game");
        tags.put("Date", String.format("%04d.%02d.%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth()));
        tags.put("X", xName);
        tags.put("O", oName);
        tags.put("Result", result);
        if (!game.start().equals(Position.start())) {
            tags.put("FEN", game.start().toFen());
        }
        return new GameRecord(tags, game.start(), game.moves());
    }

    /** Like {@link #of(Game, String, String, String, LocalDate)}, with a {@code Termination} tag saying how the game ended. */
    public static GameRecord of(Game game, String xName, String oName, String result, LocalDate date, String termination) {
        GameRecord r = of(game, xName, oName, result, date);
        return termination == null || termination.isEmpty() ? r : r.withTag("Termination", termination);
    }

    /** This record with the tag {@code name} set to {@code value} (added after the existing tags if it is new). */
    public GameRecord withTag(String name, String value) {
        Map<String, String> t = new LinkedHashMap<>(tags);
        t.put(name, value);
        return new GameRecord(t, start, moves);
    }

    /** The result tag for an outcome. */
    public static String resultTag(Outcome outcome) {
        return switch (outcome) {
            case X_WINS -> "1-0";
            case O_WINS -> "0-1";
            case DRAW -> "1/2-1/2";
            case ONGOING -> "*";
        };
    }

    /** The game as a {@link Game}, ready to continue. */
    public Game toGame() {
        Game g = new Game(start);
        for (Move m : moves) {
            g.play(m);
        }
        return g;
    }

    public String tag(String name) {
        return tags.getOrDefault(name, "");
    }

    public String toText() {
        StringBuilder sb = new StringBuilder();
        tags.forEach((k, v) -> sb.append('[').append(k).append(" \"").append(v.replace("\"", "'")).append("\"]\n"));
        sb.append('\n');
        int number = start.fullmoveNumber();
        Side mover = start.sideToMove();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < moves.size(); i++) {
            if (mover == Side.X) {
                line.append(number).append(". ");
            } else if (i == 0) {
                line.append(number).append(". ... ");
            }
            line.append(moves.get(i).toUai()).append(' ');
            if (mover == Side.O) {
                number++;
            }
            mover = mover.opponent();
        }
        String result = tag("Result");
        sb.append(line).append(result.isEmpty() ? "*" : result).append('\n');
        return sb.toString();
    }

    /**
     * Reads one game.
     *
     * @throws IllegalArgumentException if the text is not a game, holds more than one game, or contains an illegal move
     */
    public static GameRecord parse(String text) {
        List<GameRecord> all = parseAll(text);
        if (all.size() != 1) {
            throw new IllegalArgumentException("the file holds " + all.size() + " games, not one");
        }
        return all.get(0);
    }

    /**
     * Reads a file of one or more games: a new game starts at a tag line that comes after the moves of the previous one. Lines
     * starting with {@code #} are comments and are skipped.
     *
     * @throws IllegalArgumentException if there is no game, or a game has an illegal move (the message says which game)
     */
    public static List<GameRecord> parseAll(String text) {
        List<GameRecord> games = new ArrayList<>();
        List<String> block = new ArrayList<>();
        boolean hasMoves = false;
        for (String line : text.split("\\R")) {
            if (line.strip().startsWith("#")) {
                continue;
            }
            boolean tag = TAG.matcher(line).matches();
            if (tag && hasMoves) {
                games.add(parseGame(block, games.size() + 1));
                block.clear();
                hasMoves = false;
            }
            block.add(line);
            if (!tag && !line.isBlank()) {
                hasMoves = true;
            }
        }
        if (block.stream().anyMatch(l -> !l.isBlank())) {
            games.add(parseGame(block, games.size() + 1));
        }
        if (games.isEmpty()) {
            throw new IllegalArgumentException("the file is empty");
        }
        return games;
    }

    /** Several games as one text, separated by blank lines, with optional comment lines (shown as {@code # ...}) at the top. */
    public static String toText(List<GameRecord> games, List<String> header) {
        StringBuilder sb = new StringBuilder();
        for (String h : header) {
            sb.append("# ").append(h).append('\n');
        }
        if (!header.isEmpty()) {
            sb.append('\n');
        }
        for (int i = 0; i < games.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            // A game starts at its first tag line, so every game needs at least one.
            GameRecord g = games.get(i);
            sb.append((g.tags().isEmpty() ? g.withTag("Event", "?") : g).toText());
        }
        return sb.toString();
    }

    private static GameRecord parseGame(List<String> lines, int number) {
        try {
            return parseLines(lines);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("game " + number + ": " + e.getMessage(), e);
        }
    }

    private static GameRecord parseLines(List<String> lines) {
        Map<String, String> tags = new LinkedHashMap<>();
        StringBuilder moveText = new StringBuilder();
        for (String line : lines) {
            Matcher m = TAG.matcher(line);
            if (m.matches()) {
                tags.put(m.group(1), m.group(2));
            } else {
                moveText.append(line).append(' ');
            }
        }
        Position start = Position.start();
        if (tags.containsKey("FEN")) {
            try {
                start = Position.fromFen(tags.get("FEN"));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("bad FEN tag: " + e.getMessage(), e);
            }
        }
        String body = moveText.toString().replaceAll("\\{[^}]*\\}", " ").strip();
        List<Move> moves = new ArrayList<>();
        Position p = start;
        if (!body.isEmpty()) {
            for (String token : body.split("\\s+")) {
                if (MOVE_NUMBER.matcher(token).matches() || token.equals("...") || RESULTS.contains(token)) {
                    continue;
                }
                Move move;
                try {
                    move = Move.parse(token);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("not a move: " + token);
                }
                if (!p.isLegal(move)) {
                    throw new IllegalArgumentException("move " + (moves.size() + 1) + " (" + token + ") is not legal");
                }
                p = p.makeMove(move);
                moves.add(move);
            }
        }
        return new GameRecord(tags, start, moves);
    }
}
