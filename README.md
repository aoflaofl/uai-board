# uai-board

A Java user interface for playing and testing engines that speak UAI, the Universal Ataxx Interface (the UCI-style protocol used by [TikTaxx](https://github.com/kz04px/tiktaxx), [Funes](https://github.com/Tempate/Funes), [Moonbird](https://github.com/tsoj/Moonbird) and [gataxx](https://github.com/aoflaofl/gataxx)).

It uses Swing, so it needs nothing but a JDK (21 or newer): no dependencies at runtime.

## Status

Early, but you can play: choose an opponent in **Game > New Game** (another person at the same board, or one of your engines), the side you play, and the engine's time (a fixed time per move, or a game clock with an increment). Click a piece, then a highlighted square: dots are clones, rings are jumps. While the engine thinks you see its depth, score, nodes, speed and best line; the move list, undo and the clocks are on screen, and **Engine > Show Console** shows every UAI line sent and received. **Engine > Engines...** manages the engine list (name, command line, options to set, and a Test button that starts the engine and reads its options).

Settings (the engines and the last game setup) are kept in `~/.uai-board/settings.properties`.

Not there yet: flipping the board when you play o, resigning or offering a draw, a clock between two people, saving and loading games.

## Build and run

```
mvn clean verify
java -jar target/uai-board.jar
```

## Layout

- `game`: the rules, with no user interface and no engine code. `Position` (FEN, legal moves, conversions, game end), `Move` (UAI text form), `Squares`, `Game` (moves played, undo). The tests check the move generator against the reference perft counts of gataxx and libataxx (16, 256, 6460, 155888 from the start position).
- `uai`: the engine client, with no Swing in it. `UaiClient` launches an engine process, does the handshake (`UaiClient.EngineId`: name, author and the options as `UaiOption`), sets options, sends `position` and `go` (`GoParams`), and reports `info` lines (`UaiInfo`: depth, score, nodes, time, principal variation) and the `bestmove` to a `Listener` on its reader thread; it notices an engine that exits, times out an engine that stops answering, and can send bare FENs for engines that read move lists badly. It was checked against gataxx, TikTaxx, Funes and Moonbird; the unit tests use a small fake engine in a second JVM.
- `play`: what turns the pieces above into a game against an engine, still without Swing. `PlayController` runs a game (two people, or a person against an engine): it asks the engine for its moves, passes automatically for a stuck side, ignores late answers that belong to a position that has been undone, gives up on an engine that never answers, ends the game on a fallen flag or an illegal engine move, and carries on as a two-person game if the engine dies. `EngineSession` is one running engine (it never reports a cancelled search), `GameClock` the clock, `Settings` the saved engines and setup. The tests run whole games against a small fake engine.
- `ui`: `MainFrame` (menus) around `MainPanel` (board, clocks, analysis, moves, status); `BoardPanel` draws a position and reports the move the player completes, through `MoveInput` (select a piece, click a target) and `BoardGeometry` (pixels to squares), both testable without a display; `AnalysisPanel`, `NewGameDialog`, `EnginesDialog` and `EngineLogFrame`. `MainPanel` can be built and painted headless, which the tests do.

## Roadmap

1. ~~A UAI client~~ (done, `uai`).
2. ~~Play against an engine, with analysis and clocks~~ (done, `play` and `ui`).
3. Engine against engine, with a match record.
4. Load and save games (a move list with the start FEN); flip the board; resign and draw.

## Licence

MIT, see [LICENSE](LICENSE).
