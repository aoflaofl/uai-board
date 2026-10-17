package spamalot.uaiboard.uai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GoParamsTest {
    @Test
    void simpleLimits() {
        assertEquals("go movetime 100", GoParams.movetime(100).toCommand());
        assertEquals("go depth 8", GoParams.depth(8).toCommand());
        assertEquals("go nodes 5000", GoParams.nodes(5000).toCommand());
        assertEquals("go infinite", GoParams.infinite().toCommand());
    }

    @Test
    void clockUsesBlackForXAndWhiteForO() {
        assertEquals("go wtime 2000 btime 3000 winc 20 binc 30", GoParams.clock(3000, 2000, 30, 20).toCommand());
        assertEquals("go wtime 2000 btime 3000 winc 20 binc 30 movestogo 10",
                GoParams.clock(3000, 2000, 30, 20).withMovesToGo(10).toCommand());
    }
}
