# uai-board

A Java user interface for playing and testing engines that speak UAI, the Universal Ataxx Interface (the UCI-style protocol used by [TikTaxx](https://github.com/kz04px/tiktaxx), [Funes](https://github.com/Tempate/Funes), [Moonbird](https://github.com/tsoj/Moonbird) and [gataxx](https://github.com/aoflaofl/gataxx)).

It uses Swing, so it needs nothing but a JDK (21 or newer): no dependencies at runtime.

## Status

Early, but you can play: choose an opponent in **Game > New Game** (another person at the same board, or one of your engines), the side you play, and the engine's time (a fixed time per move, or a game clock with an increment). Click a piece, then a highlighted square: dots are clones, rings are jumps. While the engine thinks you see its depth, score, nodes, speed and best line; the move list, undo and the clocks are on screen, and **Engine > Show Console** shows every UAI line sent and received. **Engine > Engines...** manages the engine list (name, command line, options to set, and a Test button that starts the engine and reads its options).

Settings (the engines and the last game setup) are kept in `~/.uai-board/settings.properties`.

Also: **Game > Save Game / Load Game** keep a game as a small text file (`.ataxx`, tags and moves in a PGN-like form, with a `[FEN]` tag for a custom start); a loaded game continues as a game between two people, and you can step back through it with Undo. **Game > Resign** ends your game, and **Draw by Agreement** is available between two people (UAI has no way to offer an engine a draw). The **New Game** dialog takes an optional start position as a FEN. **View > Flip Board** turns the board around; it turns by itself when you play o against an engine.

**Engine > Analyse Position** (Ctrl+L) lets an engine analyse the position on the board without limit, in its own process, so you can use it while playing against another engine or stepping through a loaded game. It follows every move and undo, and shows depth, score (from x's point of view), nodes, speed and the best line in an Analysis panel; **Engine > Analysis Engine** picks which engine. Some engines ignore `stop` and never answer an infinite search (Moonbird does); the analyser notices and restarts such an engine for each new position, which takes about a tenth of a second.

**Engine > Engine Match…** runs engines against each other: pick engine A and engine B (each can have extra options for this match only, so you can test one engine against itself with a setting changed), a time control, the number of games and the openings (random, from a seed, or a file of FENs). Every opening is played twice with the colours swapped. You watch the games live (or switch that off for speed) with each engine's analysis and the clocks, see the score, Elo difference with its error margin and LOS update after every game, and get a list of the finished games. A match can be paused after the game in progress, resumed or stopped, and an engine that fails is started again for the next game. The window remembers its settings. **Save results…** writes the whole match as one file (a `#` summary with the engines, time control and standings, then every finished game with its round number, result and how it ended); **double-click a game** in the list to review it in the main window, where **Game > Redo** (Ctrl+Y), **Go to Start** and **Go to End** step through it, and Undo steps back. **Game > Load Game** also opens such a file: if it holds several games you pick one.

Not there yet: a clock between two people.

## Engines it has been checked with

[gataxx](https://github.com/aoflaofl/gataxx) 1.9.0, [TikTaxx](https://github.com/kz04px/tiktaxx) (`95dffff`), [Funes](https://github.com/Tempate/Funes) (`dd319f1`) and [Moonbird](https://github.com/tsoj/Moonbird) (`d6a6107`): handshake and options, playing against each of them, analysis mode, and matches of gataxx against each of the other three (20 games at 0.1 s per move, plus 10 games on a 3 s + 0.05 s clock against Funes). Every game ended by the rules, with no forfeit, and average move times stayed within 85-97 ms at a 100 ms limit.

Differences between engines the program copes with:

- **`stop` during an infinite search.** gataxx and TikTaxx answer it with a `bestmove` at once. Funes and Moonbird keep searching and never answer, so analysis mode waits 0.4 s, restarts the engine process, and from then on restarts it immediately for each new position (about 0.05-0.13 s).
- **Options.** Funes announces none; the others announce 4 to 60. Options are only set from the engine list or the match setup.
- **Names.** The name shown is the one the engine gives in its handshake (`Tiktaxx`, `Funes`, `Moonbird d6a6107`, `gataxx 1.9.0`).

## Build and run

```
mvn clean verify
java -jar target/uai-board.jar
```

## Layout

- `game`: the rules, with no user interface and no engine code. `Position` (FEN, legal moves, conversions, game end), `Move` (UAI text form), `Squares`, `Game` (moves played, undo). The tests check the move generator against the reference perft counts of gataxx and libataxx (16, 256, 6460, 155888 from the start position).
- `uai`: the engine client, with no Swing in it. `UaiClient` launches an engine process, does the handshake (`UaiClient.EngineId`: name, author and the options as `UaiOption`), sets options, sends `position` and `go` (`GoParams`), and reports `info` lines (`UaiInfo`: depth, score, nodes, time, principal variation) and the `bestmove` to a `Listener` on its reader thread; it notices an engine that exits, times out an engine that stops answering, and can send bare FENs for engines that read move lists badly. It was checked against gataxx, TikTaxx, Funes and Moonbird; the unit tests use a small fake engine in a second JVM.
- `play`: what turns the pieces above into a game against an engine, still without Swing. `PlayController` runs a game (two people, or a person against an engine): it asks the engine for its moves, passes automatically for a stuck side, ignores late answers that belong to a position that has been undone, gives up on an engine that never answers, ends the game on a fallen flag or an illegal engine move, and carries on as a two-person game if the engine dies. `EngineSession` is one running engine (it never reports a cancelled search), `GameClock` the clock, `Settings` the saved engines and setup. `game.GameRecord` reads and writes the game files. `match` holds the pieces of engine matches: `Elo`, `Openings`, `EnginePlayer` (an engine that answers one request at a time and times its answers where they are read), `GameDriver`, `MatchController`. `Analyser` is analysis mode: a separate engine on `go infinite`, restarted when the position changes (and restarting the process for an engine that ignores `stop`). The tests run whole games against a small fake engine.
- `ui`: `MainFrame` (menus) around `MainPanel` (board, clocks, analysis, moves, status); `BoardPanel` draws a position and reports the move the player completes, through `MoveInput` (select a piece, click a target) and `BoardGeometry` (pixels to squares), both testable without a display; `AnalysisPanel`, `NewGameDialog`, `EnginesDialog` and `EngineLogFrame`. `MainPanel` can be built and painted headless, which the tests do.

## Roadmap

1. ~~A UAI client~~ (done, `uai`).
2. ~~Play against an engine, with analysis and clocks~~ (done, `play` and `ui`).
3. Engine against engine, with a match record. In progress: the Elo statistics, the opening generator and file reader, the single-game driver between two engines (`match.GameDriver`: clocks or a fixed move time, forfeits for illegal moves, timeouts and crashes, automatic passes, cancellation), the match controller (`match.MatchController`: a series of games on its own thread with colours swapped over each opening, pause, stop, engines restarted after a failure, running score and Elo), multi-game game files (`GameRecord`) and the match window (`ui.MatchPanel`) are done; saving a match and reviewing its games (`match.MatchReport`) is done too; real-engine checks are done too (see above).
4. ~~Load and save games, flip the board, resign and draw, start from a FEN~~ (done).
5. ~~Analysis mode~~ (done).

## Licence

MIT, see [LICENSE](LICENSE).
