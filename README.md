# uai-board

A Java user interface for playing and testing engines that speak UAI, the Universal Ataxx Interface (the UCI-style protocol used by [TikTaxx](https://github.com/kz04px/tiktaxx), [Funes](https://github.com/Tempate/Funes), [Moonbird](https://github.com/tsoj/Moonbird) and [gataxx](https://github.com/aoflaofl/gataxx)).

It uses Swing, so it needs nothing but a JDK (21 or newer): no dependencies at runtime.

## Status

Early. Two people can play a complete game of Ataxx on one board (click a piece, then click a highlighted square: dots are clones, rings are jumps), with a move list, undo and a status line. The UAI client is written and tested but not yet connected to the window: playing against an engine is the next step.

## Build and run

```
mvn clean verify
java -jar target/uai-board.jar
```

## Layout

- `game`: the rules, with no user interface and no engine code. `Position` (FEN, legal moves, conversions, game end), `Move` (UAI text form), `Squares`, `Game` (moves played, undo). The tests check the move generator against the reference perft counts of gataxx and libataxx (16, 256, 6460, 155888 from the start position).
- `uai`: the engine client, with no Swing in it. `UaiClient` launches an engine process, does the handshake (`UaiClient.EngineId`: name, author and the options as `UaiOption`), sets options, sends `position` and `go` (`GoParams`), and reports `info` lines (`UaiInfo`: depth, score, nodes, time, principal variation) and the `bestmove` to a `Listener` on its reader thread; it notices an engine that exits, times out an engine that stops answering, and can send bare FENs for engines that read move lists badly. It was checked against gataxx, TikTaxx, Funes and Moonbird; the unit tests use a small fake engine in a second JVM.
- `ui`: `BoardPanel` draws a position and reports the move the player completes; `MoveInput` turns clicks into moves (select a piece, click a target) and `BoardGeometry` maps pixels to squares, both without needing a display, so they are unit tested; `MainFrame` is the window.

## Roadmap

1. ~~A UAI client~~ (done, see `uai`).
2. Play against an engine: choose the engine and the side, show its depth, score and principal variation, game clocks.
3. Engine against engine, with a match record.
4. Load and save games (a move list with the start FEN).

## Licence

MIT, see [LICENSE](LICENSE).
