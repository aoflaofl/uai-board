package spamalot.uaiboard.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import spamalot.uaiboard.uai.UaiInfo;

class DisplayFormatTest {
    @Test
    void clocksShowMinutesAndTenthsBelowTenSeconds() {
        assertEquals("5:00", MainPanel.formatClock(300_000));
        assertEquals("4:59", MainPanel.formatClock(299_999));
        assertEquals("1:02:03", MainPanel.formatClock(3_723_000));
        assertEquals("0:09.9", MainPanel.formatClock(9_999));
        assertEquals("0:00.0", MainPanel.formatClock(0));
        assertEquals("0:00.0", MainPanel.formatClock(-500));
        assertEquals("0:10", MainPanel.formatClock(10_000));
    }

    @Test
    void countsAreAbbreviated() {
        assertEquals("950", AnalysisPanel.formatCount(950));
        assertEquals("9999", AnalysisPanel.formatCount(9_999));
        assertEquals("12.3k", AnalysisPanel.formatCount(12_345));
        assertEquals("2.58M", AnalysisPanel.formatCount(2_580_000));
        assertEquals("1.50G", AnalysisPanel.formatCount(1_500_000_000L));
    }

    @Test
    void scoresShowASignOrMate() {
        assertEquals("+63", AnalysisPanel.formatScore(new UaiInfo.Score(false, 63)));
        assertEquals("-12", AnalysisPanel.formatScore(new UaiInfo.Score(false, -12)));
        assertEquals("+0", AnalysisPanel.formatScore(new UaiInfo.Score(false, 0)));
        assertEquals("mate -3", AnalysisPanel.formatScore(new UaiInfo.Score(true, -3)));
    }

    @Test
    void analysisScoresAreShownFromXsPointOfView() {
        UaiInfo.Score s = new UaiInfo.Score(false, 40);
        assertEquals("+40", AnalysisPanel.formatScore(AnalysisPanel.xView(s, spamalot.uaiboard.game.Side.X)));
        assertEquals("-40", AnalysisPanel.formatScore(AnalysisPanel.xView(s, spamalot.uaiboard.game.Side.O)));
        assertEquals("mate 2", AnalysisPanel.formatScore(AnalysisPanel.xView(new UaiInfo.Score(true, -2),
                spamalot.uaiboard.game.Side.O)));
    }
}
