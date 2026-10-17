package spamalot.uaiboard.uai;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Position;

/**
 * A tiny UAI engine for the client tests: it plays the first legal move. The one argument picks a misbehaviour: {@code silent}
 * never answers {@code uai}; {@code crash} exits on {@code go}; {@code badmove} answers {@code bestmove zz}; {@code slowready}
 * answers {@code isready} only after two seconds; {@code slow} thinks for 400 ms before every move; {@code illegal} answers
 * {@code bestmove d4}, which is illegal at the start; {@code mute} never answers {@code go}; {@code chatty}
 * prints a late {@code info string late} line when it is stopped; {@code deaf} ignores {@code stop}.
 */
public final class FakeEngine {
    private FakeEngine() {}

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "normal";
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        Position position = Position.start();
        boolean searching = false;
        String line;
        while ((line = in.readLine()) != null) {
            String[] t = line.trim().split("\\s+");
            switch (t[0]) {
                case "uai" -> {
                    if (mode.equals("silent")) {
                        continue;
                    }
                    out("id name FakeEngine 1.0");
                    out("id author Test Author");
                    out("option name Hash type spin default 16 min 1 max 1024");
                    out("option name Style type combo default calm var calm var wild");
                    out("option name Clear Hash type button");
                    out("uaiok");
                }
                case "isready" -> {
                    if (mode.equals("slowready")) {
                        Thread.sleep(2000);
                    }
                    out("readyok");
                }
                case "setoption" -> out("info string got " + line.trim());
                case "position" -> {
                    out("info string got " + line.trim());
                    position = parsePosition(t);
                }
                case "go" -> {
                    if (mode.equals("crash")) {
                        System.exit(3);
                    }
                    Move first = position.legalMoves().get(0);
                    out("info depth 1 score cp 12 nodes 10 time 1 pv " + first);
                    out("info depth 2 nodes 99 nps 5000 hashfull 3 score mate -2 time 2 pv " + first);
                    if (mode.equals("mute")) {
                        continue;
                    }
                    if (line.contains("infinite")) {
                        searching = true;
                    } else {
                        if (mode.equals("slow")) {
                            Thread.sleep(400);
                        }
                        out(mode.equals("badmove") ? "bestmove zz" : mode.equals("illegal") ? "bestmove d4" : "bestmove " + first);
                    }
                }
                case "stop" -> {
                    if (searching && !mode.equals("deaf")) {
                        searching = false;
                        if (mode.equals("chatty")) {
                            out("info string late");
                        }
                        out("bestmove " + position.legalMoves().get(0));
                    }
                }
                case "quit" -> {
                    return;
                }
                default -> { }
            }
        }
    }

    private static Position parsePosition(String[] t) {
        int i = 1;
        Position p;
        if (t[i].equals("startpos")) {
            p = Position.start();
            i++;
        } else {
            // "position fen <board> <side> <half> <full> [moves ...]"
            p = Position.fromFen(String.join(" ", List.of(t).subList(2, 6)));
            i = 6;
        }
        if (i < t.length && t[i].equals("moves")) {
            for (i++; i < t.length; i++) {
                p = p.makeMove(Move.parse(t[i]));
            }
        }
        return p;
    }

    private static void out(String s) {
        System.out.println(s);
        System.out.flush();
    }
}
