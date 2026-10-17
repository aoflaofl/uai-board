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
import spamalot.uaiboard.game.GameRecord;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.play.FakeEngines;
import spamalot.uaiboard.play.GameSetup;
import spamalot.uaiboard.play.Settings;

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

    @Test
    void analysisModeTurnsOnFollowsMovesAndTurnsOff() throws Exception {
        Path file = dir.resolve("analysis.properties");
        new Settings(java.util.List.of(FakeEngines.config("normal")), GameSetup.DEFAULT).save(file);
        MainPanel[] holder = new MainPanel[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new MainPanel(file));
        MainPanel panel = holder[0];
        boolean[] told = new boolean[1];
        SwingUtilities.invokeAndWait(() -> {
            panel.setAnalysisListener(on -> told[0] = on);
            assertEquals(java.util.List.of("fake-normal"), panel.analysisEngineNames());
            assertEquals("fake-normal", panel.analysisEngineName());
            panel.setAnalysisEnabled(true);
            assertTrue(panel.isAnalysing());
            assertTrue(told[0]);
            panel.controller().humanMove(Move.parse("b6")); // the analysis follows the position
        });
        Thread.sleep(500);
        SwingUtilities.invokeAndWait(() -> {
            panel.chooseAnalysisEngine("fake-normal");
            panel.setAnalysisEnabled(false);
            assertEquals(false, panel.isAnalysing());
            assertEquals(false, told[0]);
        });
        assertEquals("fake-normal", Settings.load(file).analysisEngine());
        SwingUtilities.invokeAndWait(panel::close);
    }

    private static int movesOf(MainPanel p) {
        return p.controller().game().moves().size();
    }

    @Test
    void undoRedoAndSteppingThroughAGame() throws Exception {
        MainPanel panel = newPanel();
        SwingUtilities.invokeAndWait(() -> {
            panel.humanMove(Move.parse("b6"));
            panel.humanMove(Move.parse("b2"));
            panel.humanMove(Move.parse("a7c5"));
            assertEquals(false, panel.canRedo());
            panel.undo();
            panel.undo();
            assertEquals(1, movesOf(panel));
            assertTrue(panel.canRedo());
            panel.redo();
            assertEquals(2, movesOf(panel));
            panel.goToStart();
            assertEquals(0, movesOf(panel));
            panel.goToEnd();
            assertEquals(3, movesOf(panel));
            assertEquals(false, panel.canRedo());
            // A new move after stepping back ends the way forward.
            panel.undo();
            assertTrue(panel.canRedo());
            panel.humanMove(Move.parse("a7c6"));
            assertEquals(false, panel.canRedo());
            assertEquals(3, movesOf(panel));
        });
        SwingUtilities.invokeAndWait(panel::close);
    }

    private static GameRecord record(String text) {
        return GameRecord.parse(text);
    }

    @Test
    void reviewingAGameStartsAtTheBeginningAndStepsForward() throws Exception {
        MainPanel panel = newPanel();
        GameRecord r = record("[X \"gataxx\"]\n[O \"Moonbird\"]\n[Result \"1-0\"]\n[Termination \"time forfeit\"]\n"
                + "[Round \"4\"]\n\n1. b6 b2 2. a7c5 f6 1-0\n");
        SwingUtilities.invokeAndWait(() -> {
            panel.reviewRecord(r);
            assertEquals(0, movesOf(panel));
            assertTrue(panel.canRedo());
            assertTrue(panel.statusText().contains("x gataxx vs o Moonbird, round 4, 1-0 (time forfeit)"), panel.statusText());
            panel.goToEnd();
            assertEquals(4, movesOf(panel));
            assertEquals(r.toGame().position(), panel.controller().game().position());
        });
        SwingUtilities.invokeAndWait(panel::close);
    }

    @Test
    void aFileWithSeveralGamesAsksWhichOneToLoad() throws Exception {
        MainPanel panel = newPanel();
        Path file = dir.resolve("many.ataxx");
        java.nio.file.Files.writeString(file, GameRecord.toText(java.util.List.of(record("1. b6 b2"), record("1. f1 f6 2. g2"),
                record("1. a7c5")), java.util.List.of("a match")));
        java.util.List<String> asked = new java.util.ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            panel.setGameChooser(games -> {
                asked.add(games.size() + " games");
                return 1;
            });
            assertEquals(null, panel.loadFrom(file));
            assertEquals(3, movesOf(panel)); // the second game: f1 f6 g2
            panel.setGameChooser(games -> -1); // cancelled: nothing changes
            assertEquals(null, panel.loadFrom(file));
            assertEquals(3, movesOf(panel));
            panel.setGameChooser(games -> {
                throw new AssertionError("a single game is loaded without asking");
            });
            Path one = dir.resolve("one.ataxx");
            try {
                java.nio.file.Files.writeString(one, record("1. b6 b2").toText());
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            assertEquals(null, panel.loadFrom(one));
            assertEquals(2, movesOf(panel));
        });
        assertEquals(java.util.List.of("3 games"), asked);
        SwingUtilities.invokeAndWait(panel::close);
    }

    @Test
    void gamesAreListedWithTheirTagsForTheChooser() {
        GameRecord r = record("[X \"A\"]\n[O \"B\"]\n[Result \"0-1\"]\n[Round \"7\"]\n\n1. b6 b2 2. a7c5");
        assertEquals("x A vs o B, round 7, 0-1", MainPanel.describe(r));
        assertEquals("3. x A vs o B, round 7, 0-1  [3 moves]", MainPanel.listLabel(3, r));
        assertEquals("", MainPanel.describe(record("1. b6")));
        assertEquals("2. game  [1 moves]", MainPanel.listLabel(2, record("1. b6")));
    }
}
