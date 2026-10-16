# uai-board

A Java user interface for playing and testing engines that speak UAI, the Universal Ataxx Interface (the UCI-style protocol used by [TikTaxx](https://github.com/kz04px/tiktaxx), [Funes](https://github.com/Tempate/Funes), [Moonbird](https://github.com/tsoj/Moonbird) and [gataxx](https://github.com/aoflaofl/gataxx)).

**Status:** just started; no code yet.

## Goals

- Show the 7x7 Ataxx board, let a person play against any UAI engine by clicking, and show the engine's analysis (depth, score, principal variation).
- Run engine against engine, with a clock.
- Keep engine handling generic: launch any UAI engine from a command line and set its options.
- Java 21, no dependencies beyond the JDK if possible.

## Licence

MIT, see [LICENSE](LICENSE).
