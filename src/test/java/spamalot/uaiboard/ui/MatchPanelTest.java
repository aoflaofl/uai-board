package spamalot.uaiboard.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spamalot.uaiboard.match.MatchController;
import spamalot.uaiboard.play.EngineConfig;
import spamalot.uaiboard.play.FakeEngines;
import spamalot.uaiboard.play.MatchPrefs;
import spamalot.uaiboard.play.TimeControl;

/** The match window's content, built and driven without a display, with fake engines. */
class MatchPanelTest {
    @TempDir
    Path dir;

    private final List<MatchPrefs> savedPrefs = Collections.synchronizedList(new ArrayList<>());
    private final List<String> problems = Collections.synchronizedList(new ArrayList<>());
    private final List<spamalot.uaiboard.game.GameRecord> reviewed = Collections.synchronizedList(new ArrayList<>());
    private MatchPanel panel;

    @AfterEach
    void close() throws Exception {
        if (panel != null) {
            SwingUtilities.invokeAndWait(panel::close);
        }
    }

    private MatchPanel panel(List<EngineConfig> engines, MatchPrefs prefs) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            panel = new MatchPanel(engines, prefs, reviewed::add, savedPrefs::add, e -> { });
            panel.setProblemSink(problems::add);
        });
        return panel;
    }

    private static MatchPrefs prefs(int games) {
        return new MatchPrefs("fake-normal", "fake-chatty", new TimeControl.MoveTime(20), games, 4, 5, 1000, "", "", "");
    }

    private static List<EngineConfig> engines() {
        return List.of(FakeEngines.config("normal"), FakeEngines.config("chatty"));
    }

    private <T> T onEdt(Supplier<T> s) throws Exception {
        Object[] box = new Object[1];
        SwingUtilities.invokeAndWait(() -> box[0] = s.get());
        @SuppressWarnings("unchecked") T t = (T) box[0];
        return t;
    }

    private void await(BooleanSupplier c) throws Exception {
        for (int i = 0; i < 600; i++) {
            if (onEdt(c::getAsBoolean)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("condition not reached");
    }

    @Test
    void aMatchRunsToTheEndAndFillsTheTableAndTheScore() throws Exception {
        panel(engines(), prefs(2));
        assertTrue(onEdt(panel::startEnabled));
        assertFalse(onEdt(panel::stopEnabled));
        SwingUtilities.invokeAndWait(panel::startMatch);
        assertFalse(onEdt(panel::startEnabled), "setup is locked while the match runs");
        await(() -> panel.matchState() == MatchController.State.FINISHED && panel.finishedRows() == 2
                && panel.startEnabled());
        assertEquals(1, onEdt(() -> panel.tableValue(0, 0)));
        assertEquals(2, onEdt(() -> panel.tableValue(1, 0)));
        assertEquals("game over", onEdt(() -> panel.tableValue(0, 4)));
        String standings = onEdt(panel::standingsText);
        assertTrue(standings.contains("fake-normal vs fake-chatty"), standings);
        assertTrue(standings.contains(" / 2 "), standings);
        assertTrue(onEdt(panel::statusText).startsWith("Match finished"), onEdt(panel::statusText));
        assertEquals(1, savedPrefs.size());
        assertEquals(prefs(2), savedPrefs.get(0));
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void theMatchCanBeSavedAndItsGamesReviewed() throws Exception {
        panel(engines(), prefs(2));
        assertTrue(onEdt(() -> panel.saveResultsTo(dir.resolve("none.ataxx"))).contains("No game has finished"));
        SwingUtilities.invokeAndWait(panel::startMatch);
        await(() -> panel.matchState() == MatchController.State.FINISHED && panel.finishedRows() == 2);
        Path file = dir.resolve("match.ataxx");
        assertEquals(null, onEdt(() -> panel.saveResultsTo(file)));
        String text = Files.readString(file);
        assertTrue(text.startsWith("# uai-board engine match\n"), text);
        assertTrue(text.contains("# Games: 2 of 2 played (finished)"), text);
        List<spamalot.uaiboard.game.GameRecord> games = spamalot.uaiboard.game.GameRecord.parseAll(text);
        assertEquals(2, games.size());
        assertEquals("fake-normal", games.get(0).tag("X"));
        assertEquals("fake-chatty", games.get(1).tag("X"));
        assertEquals("1", games.get(0).tag("Round"));

        SwingUtilities.invokeAndWait(() -> panel.reviewRow(1));
        assertEquals(1, reviewed.size());
        assertEquals(games.get(1).moves(), reviewed.get(0).moves());
        assertEquals("2", reviewed.get(0).tag("Round"));
        SwingUtilities.invokeAndWait(() -> panel.reviewRow(5)); // no such row: ignored
        assertEquals(1, reviewed.size());
    }

    @Test
    void stoppingEndsTheMatchAndUnlocksTheSetup() throws Exception {
        MatchPrefs slow = new MatchPrefs("fake-slow", "fake-slow", new TimeControl.MoveTime(1000), 10, 4, 5, 1000, "", "", "");
        panel(List.of(FakeEngines.config("slow")), slow);
        SwingUtilities.invokeAndWait(panel::startMatch);
        await(() -> panel.statusText().startsWith("Game 1 of 10"));
        assertTrue(onEdt(panel::stopEnabled));
        SwingUtilities.invokeAndWait(() -> panel.stopForTest());
        await(() -> panel.matchState() == MatchController.State.STOPPED && panel.startEnabled());
        assertEquals(0, onEdt(panel::finishedRows));
        assertTrue(onEdt(panel::statusText).startsWith("Match stopped"), onEdt(panel::statusText));
    }

    @Test
    void problemsWithTheSetupAreReportedAndNothingStarts() throws Exception {
        panel(List.of(), prefs(2));
        SwingUtilities.invokeAndWait(panel::startMatch);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("Choose both engines"), problems.get(0));

        MatchPrefs badOptions = new MatchPrefs("fake-normal", "fake-chatty", new TimeControl.MoveTime(20), 2, 4, 5, 1000,
                "this is not an option", "", "");
        SwingUtilities.invokeAndWait(() -> panel.close());
        problems.clear();
        panel(engines(), badOptions);
        SwingUtilities.invokeAndWait(panel::startMatch);
        assertTrue(problems.get(0).contains("not an option"), problems.toString());
        assertTrue(onEdt(panel::startEnabled));

        Path missing = dir.resolve("none.txt");
        MatchPrefs badFile = new MatchPrefs("fake-normal", "fake-chatty", new TimeControl.MoveTime(20), 2, 4, 5, 1000, "", "",
                missing.toString());
        problems.clear();
        panel(engines(), badFile);
        SwingUtilities.invokeAndWait(panel::startMatch);
        assertTrue(problems.get(0).contains("Cannot read the opening file"), problems.toString());

        Path empty = dir.resolve("empty.txt");
        Files.writeString(empty, "# nothing\n");
        problems.clear();
        panel(engines(), new MatchPrefs("fake-normal", "fake-chatty", new TimeControl.MoveTime(20), 2, 4, 5, 1000, "", "",
                empty.toString()));
        SwingUtilities.invokeAndWait(panel::startMatch);
        assertTrue(problems.get(0).contains("no positions"), problems.toString());
        assertTrue(savedPrefs.isEmpty(), "nothing was saved for a setup that did not start");
    }

    @Test
    void openingsCanComeFromAFile() throws Exception {
        Path fens = dir.resolve("openings.txt");
        Files.writeString(fens, "x5o/7/7/7/7/7/o5x x 0 1\n7/7/7/7/7/7/o5x x 0 1\n");
        panel(engines(), new MatchPrefs("fake-normal", "fake-chatty", new TimeControl.MoveTime(20), 2, 4, 5, 1000, "", "",
                fens.toString()));
        SwingUtilities.invokeAndWait(panel::startMatch);
        await(() -> panel.matchState() == MatchController.State.FINISHED && panel.finishedRows() == 2);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(fens.toString(), savedPrefs.get(0).openingFile());
    }

    @Test
    void timeControlPanelKeepsWhatItIsGiven() throws Exception {
        TimeControlPanel[] box = new TimeControlPanel[1];
        SwingUtilities.invokeAndWait(() -> box[0] = new TimeControlPanel(new TimeControl.Clock(180_000, 2000)));
        assertEquals(new TimeControl.Clock(180_000, 2000), onEdt(box[0]::timeControl));
        SwingUtilities.invokeAndWait(() -> box[0].setTimeControl(new TimeControl.MoveTime(250)));
        assertEquals(new TimeControl.MoveTime(250), onEdt(box[0]::timeControl));
    }
}
