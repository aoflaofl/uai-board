package spamalot.uaiboard.play;

import spamalot.uaiboard.game.Side;

/**
 * Who plays a game: an engine (or null for two people at one board), the side the human plays against it, and the time
 * control the engine is held to.
 */
public record GameSetup(String engineName, Side humanSide, TimeControl timeControl) {
    public static final GameSetup DEFAULT = new GameSetup(null, Side.X, new TimeControl.MoveTime(1000));

    public boolean vsEngine() {
        return engineName != null;
    }
}
