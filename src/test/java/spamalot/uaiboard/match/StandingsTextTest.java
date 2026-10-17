package spamalot.uaiboard.match;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StandingsTextTest {
    @Test
    void emptyStandingsHaveOnlyTheFirstThreeLines() {
        List<String> l = StandingsText.lines("A", "B", Standings.empty());
        assertEquals(List.of("A vs B", "A: 0.0 / 0  (+0 =0 -0)  0.0%", "Elo +0.0 +/- inf   LOS 50.0%"), l);
    }

    @Test
    void aRunningMatchShowsScoreEloTerminationsAndTimes() {
        Standings s = new Standings(606, 0, 594, Map.of("game over", 1190, "time forfeit", 10), 600_000_000_000L,
                660_000_000_000L, 12000, 12000);
        List<String> l = StandingsText.lines("gataxx", "Moonbird", s);
        assertEquals("gataxx vs Moonbird", l.get(0));
        assertEquals("gataxx: 606.0 / 1200  (+606 =0 -594)  50.5%", l.get(1));
        assertEquals("Elo +3.5 +/- 19.7   LOS 63.5%", l.get(2));
        assertEquals("Games ended by: game over 1190, time forfeit 10", l.get(3));
        assertEquals("Average move time: gataxx 50 ms, Moonbird 55 ms", l.get(4));
    }
}
