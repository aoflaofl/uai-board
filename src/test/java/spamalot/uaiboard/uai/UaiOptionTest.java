package spamalot.uaiboard.uai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class UaiOptionTest {
    @Test
    void parsesSpin() {
        UaiOption o = UaiOption.parse("option name Hash type spin default 16 min 0 max 1024");
        assertEquals("Hash", o.name());
        assertEquals("spin", o.type());
        assertEquals("16", o.defaultValue());
        assertEquals(0L, o.min());
        assertEquals(1024L, o.max());
    }

    @Test
    void parsesNamesWithSpacesComboAndStrings() {
        UaiOption button = UaiOption.parse("option name Clear Hash type button");
        assertEquals("Clear Hash", button.name());
        assertEquals("button", button.type());
        UaiOption combo = UaiOption.parse("option name Play Style type combo default calm var calm var very wild");
        assertEquals("calm", combo.defaultValue());
        assertEquals(List.of("calm", "very wild"), combo.choices());
        UaiOption str = UaiOption.parse("option name PairFile type string default <empty>");
        assertEquals("<empty>", str.defaultValue());
    }

    @Test
    void rejectsOtherLines() {
        assertNull(UaiOption.parse("id name Foo"));
        assertNull(UaiOption.parse("option name Broken"));
    }
}
