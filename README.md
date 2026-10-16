# uai-board

A Java user interface for playing and testing engines that speak UAI, the Universal Ataxx Interface (the UCI-style protocol used by [TikTaxx](https://github.com/kz04px/tiktaxx), [Funes](https://github.com/Tempate/Funes), [Moonbird](https://github.com/tsoj/Moonbird) and [gataxx](https://github.com/aoflaofl/gataxx)).

It uses Swing, so it needs nothing but a JDK (21 or newer): no dependencies at runtime.

## Status

Early scaffold. Two people can play a complete game of Ataxx on one board (click a piece, then click a highlighted square: dots are clones, rings are jumps), with a move list, undo and a status line. Playing against a UAI engine is the next step.

## Build and run

```
mvn clean verify
java -jar target/uai-board.jar
```

## Layout

- `game`: the rules, with no user interface and no engine code. `Position` (FEN, legal moves, conversions, game end), `Move` (UAI text form), `Squares`, `Game` (moves played, undo). The tests check the move generator against the reference perft counts of gataxx and libataxx (16, 256, 6460, 155888 from the start position).
- `ui`: `BoardPanel` draws a position and reports the move the player completes; `MoveInput` turns clicks into moves (select a piece, click a target) and `BoardGeometry` maps pixels to squares, both without needing a display, so they are unit tested; `MainFrame` is the window.

## Roadmap

1. A UAI client: launch an engine process from a command line, handshake (`uai`/`uaiok`), set options, `position`, `go`, `bestmove`, `info` lines, always off the UI thread.
2. Play against an engine: choose the engine and the side, show its depth, score and principal variation, game clocks.
3. Engine against engine, with a match record.
4. Load and save games (a move list with the start FEN).

## Licence

MIT, see [LICENSE](LICENSE).
