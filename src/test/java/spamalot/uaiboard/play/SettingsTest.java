package spamalot.uaiboard.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spamalot.uaiboard.game.Side;

class SettingsTest {
    @TempDir
    Path dir;

    @Test
    void roundTripsEnginesOptionsAndTheLastSetup() throws IOException {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("Hash", "64");
        options.put("Clear Hash", "x");
        options.put("Zebra", "1|2");
        options.put("Alpha", "last one");
        Settings s = new Settings(List.of(new EngineConfig("gataxx", "java -jar \"/a b/g.jar\"", options),
                new EngineConfig("Funes", "/opt/funes")), new GameSetup("Funes", Side.O, new TimeControl.Clock(300_000, 2000)));
        Path file = dir.resolve("sub/settings.properties");
        s.save(file);
        Settings back = Settings.load(file);
        assertEquals(s, back);
        assertNull(back.analysisEngine());
        Settings withAnalysis = s.withAnalysisEngine("Funes");
        withAnalysis.save(file);
        assertEquals("Funes", Settings.load(file).analysisEngine());
        assertEquals("Funes", withAnalysis.withEngines(List.of()).withLastSetup(GameSetup.DEFAULT).analysisEngine());
        assertEquals(List.of("Hash", "Clear Hash", "Zebra", "Alpha"), List.copyOf(back.engine("gataxx").options().keySet()));
        assertEquals("1|2", back.engine("gataxx").options().get("Zebra"));
        assertEquals("/opt/funes", back.engine("Funes").command());
    }

    @Test
    void aMissingOrDamagedFileGivesDefaults() throws IOException {
        assertEquals(Settings.empty(), Settings.load(dir.resolve("nope.properties")));
        Path bad = dir.resolve("bad.properties");
        Files.writeString(bad, "engine.0.name=x\nengine.0.command=y\nlast.timeControl=garbage\nlast.side=zzz\n");
        Settings s = Settings.load(bad);
        assertEquals(1, s.engines().size());
        assertEquals(Side.X, s.lastSetup().humanSide());
        assertEquals(GameSetup.DEFAULT.timeControl(), s.lastSetup().timeControl());
        assertNull(s.lastSetup().engineName());
    }

    @Test
    void timeControlsRoundTripAndRejectNonsense() {
        assertEquals(new TimeControl.MoveTime(250), TimeControl.parse(new TimeControl.MoveTime(250).format(), null));
        assertEquals(new TimeControl.Clock(60_000, 0), TimeControl.parse("clock:60000:0", null));
        assertNull(TimeControl.parse("clock:0:0", null));
        assertNull(TimeControl.parse("movetime:-1", null));
        assertNull(TimeControl.parse("whatever", null));
        assertTrue(new GameSetup("x", Side.X, GameSetup.DEFAULT.timeControl()).vsEngine());
    }
}
