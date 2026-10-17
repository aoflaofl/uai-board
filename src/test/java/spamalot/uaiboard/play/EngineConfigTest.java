package spamalot.uaiboard.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EngineConfigTest {
    @Test
    void optionsParseInOrderAndSkipCommentsAndBlankLines() {
        Map<String, String> o = EngineConfig.parseOptions("Hash=64\n\n# a comment\nClear Hash = x\r\nPairFile=/a=b/c\n");
        assertEquals(List.of("Hash", "Clear Hash", "PairFile"), List.copyOf(o.keySet()));
        assertEquals("x", o.get("Clear Hash"));
        assertEquals("/a=b/c", o.get("PairFile"));
    }

    @Test
    void badOptionLinesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> EngineConfig.parseOptions("Hash 64"));
        assertThrows(IllegalArgumentException.class, () -> EngineConfig.parseOptions("=5"));
    }

    @Test
    void optionsTextRoundTrips() {
        EngineConfig e = new EngineConfig("e", "x", EngineConfig.parseOptions("A=1\nB=two words\n"));
        assertEquals("A=1\nB=two words\n", e.optionsText());
        assertEquals(e.options(), EngineConfig.parseOptions(e.optionsText()));
        assertEquals(List.of("x"), e.commandLine());
    }
}
