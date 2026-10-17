package spamalot.uaiboard.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spamalot.uaiboard.game.Move;

/** Builds the whole window content without a display, plays a move, paints it and closes it. */
class MainPanelTest {
    @TempDir
    Path dir;

    @Test
    void buildsPlaysAndPaintsHeadless() throws Exception {
        MainPanel[] holder = new MainPanel[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new MainPanel(dir.resolve("settings.properties")));
        MainPanel panel = holder[0];
        SwingUtilities.invokeAndWait(() -> {
            panel.controller().humanMove(Move.parse("b6"));
            panel.setSize(new Dimension(820, 600));
            panel.doLayout();
            for (java.awt.Component c : panel.getComponents()) {
                c.doLayout();
            }
            BufferedImage img = new BufferedImage(820, 600, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            panel.paint(g);
            g.dispose();
            Set<Integer> colours = new HashSet<>();
            for (int x = 0; x < img.getWidth(); x += 7) {
                for (int y = 0; y < img.getHeight(); y += 7) {
                    colours.add(img.getRGB(x, y));
                }
            }
            assertTrue(colours.size() > 4, "the panel painted something: " + colours.size() + " colours");
            assertEquals(1, panel.controller().game().moves().size());
        });
        SwingUtilities.invokeAndWait(panel::close);
    }

    private MainPanel newPanel() throws Exception {
        MainPanel[] holder = new MainPanel[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new MainPanel(dir.resolve("s.properties")));
        return holder[0];
    }

    @Test
    void savesAndLoadsAGame() throws Exception {
        MainPanel panel = newPanel();
        Path file = dir.resolve("g.ataxx");
        SwingUtilities.invokeAndWait(() -> {
            panel.controller().humanMove(Move.parse("b6"));
            panel.controller().humanMove(Move.parse("b2"));
            panel.controller().humanMove(Move.parse("a7c5"));
            assertEquals(null, panel.saveTo(file));
        });
        String text = java.nio.file.Files.readString(file);
        assertTrue(text.contains("1. b6 b2 2. a7c5"), text);
        assertTrue(text.contains("[Result \"*\"]"), text);

        MainPanel other = newPanel();
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(null, other.loadFrom(file));
            assertEquals(3, other.controller().game().moves().size());
            assertEquals(panel.controller().game().position(), other.controller().game().position());
        });
        SwingUtilities.invokeAndWait(panel::close);
        SwingUtilities.invokeAndWait(other::close);
    }

    @Test
    void badFilesGiveAMessageAndLeaveTheGameAlone() throws Exception {
        MainPanel panel = newPanel();
        Path bad = dir.resolve("bad.ataxx");
        java.nio.file.Files.writeString(bad, "1. b6 d4\n");
        SwingUtilities.invokeAndWait(() -> {
            panel.controller().humanMove(Move.parse("b6"));
            String problem = panel.loadFrom(bad);
            assertTrue(problem.contains("not legal"), problem);
            assertTrue(panel.loadFrom(dir.resolve("missing.ataxx")).startsWith("Could not load the game"));
            assertEquals(1, panel.controller().game().moves().size());
        });
        SwingUtilities.invokeAndWait(panel::close);
    }

    @Test
    void flippingTurnsTheBoardAndTellsTheListener() throws Exception {
        MainPanel panel = newPanel();
        boolean[] told = new boolean[1];
        SwingUtilities.invokeAndWait(() -> {
            panel.setFlipListener(f -> told[0] = f);
            panel.setFlipped(true);
            assertTrue(panel.board().isFlipped());
            assertTrue(told[0]);
            assertTrue(panel.isFlipped());
            panel.setFlipped(false);
            assertEquals(false, panel.board().isFlipped());
        });
        SwingUtilities.invokeAndWait(panel::close);
    }
}
