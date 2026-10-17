package spamalot.uaiboard.play;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import spamalot.uaiboard.game.Side;

/** The engines the user has set up and the last game setup, kept in a properties file. */
public record Settings(List<EngineConfig> engines, GameSetup lastSetup, String analysisEngine, MatchPrefs matchPrefs) {
    public Settings {
        engines = List.copyOf(engines);
    }

    /** Settings without an analysis engine chosen and with the default match setup. */
    public Settings(List<EngineConfig> engines, GameSetup lastSetup) {
        this(engines, lastSetup, null, MatchPrefs.DEFAULT);
    }

    public Settings(List<EngineConfig> engines, GameSetup lastSetup, String analysisEngine) {
        this(engines, lastSetup, analysisEngine, MatchPrefs.DEFAULT);
    }

    public Settings withAnalysisEngine(String name) {
        return new Settings(engines, lastSetup, name, matchPrefs);
    }

    public Settings withEngines(List<EngineConfig> newEngines) {
        return new Settings(newEngines, lastSetup, analysisEngine, matchPrefs);
    }

    public Settings withLastSetup(GameSetup setup) {
        return new Settings(engines, setup, analysisEngine, matchPrefs);
    }

    public Settings withMatchPrefs(MatchPrefs prefs) {
        return new Settings(engines, lastSetup, analysisEngine, prefs);
    }

    public static Settings empty() {
        return new Settings(List.of(), GameSetup.DEFAULT);
    }

    /** {@code ~/.uai-board/settings.properties}. */
    public static Path defaultFile() {
        return Path.of(System.getProperty("user.home"), ".uai-board", "settings.properties");
    }

    public EngineConfig engine(String name) {
        return engines.stream().filter(e -> e.name().equals(name)).findFirst().orElse(null);
    }

    /** Reads {@code file}; a missing or unreadable file gives empty settings, and damaged entries are skipped. */
    public static Settings load(Path file) {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException | RuntimeException e) {
            return empty();
        }
        List<EngineConfig> engines = new ArrayList<>();
        for (int i = 0; ; i++) {
            String name = p.getProperty("engine." + i + ".name");
            if (name == null) {
                break;
            }
            String command = p.getProperty("engine." + i + ".command", "");
            Map<String, String> options = new LinkedHashMap<>();
            String prefix = "engine." + i + ".option.";
            p.stringPropertyNames().stream().filter(k -> k.startsWith(prefix)).sorted().forEach(k -> {
                String value = p.getProperty(k);
                // Option order is kept in the value: "<index>:<value>"
                options.put(k.substring(prefix.length()), value);
            });
            engines.add(new EngineConfig(name, command, orderedOptions(options)));
        }
        String engineName = p.getProperty("last.engine");
        if (engineName != null && engineName.isEmpty()) {
            engineName = null;
        }
        Side side = "o".equals(p.getProperty("last.side")) ? Side.O : Side.X;
        TimeControl tc = TimeControl.parse(p.getProperty("last.timeControl", ""), GameSetup.DEFAULT.timeControl());
        String analysis = p.getProperty("analysis.engine");
        MatchPrefs d = MatchPrefs.DEFAULT;
        MatchPrefs match = new MatchPrefs(blankToNull(p.getProperty("match.engineA")), blankToNull(p.getProperty("match.engineB")),
                TimeControl.parse(p.getProperty("match.timeControl", ""), d.timeControl()),
                integer(p, "match.games", d.games(), 1, 1_000_000), integer(p, "match.plies", d.plies(), 0, 100),
                (long) integer(p, "match.seed", (int) d.seed(), Integer.MIN_VALUE, Integer.MAX_VALUE),
                integer(p, "match.grace", (int) d.graceMs(), 0, 600_000), p.getProperty("match.extraA", "").replace("\\n", "\n"),
                p.getProperty("match.extraB", "").replace("\\n", "\n"), p.getProperty("match.openingFile", ""));
        return new Settings(engines, new GameSetup(engineName, side, tc), analysis == null || analysis.isEmpty() ? null : analysis,
                match);
    }

    private static String blankToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static int integer(Properties p, String key, int fallback, int min, int max) {
        try {
            int v = Integer.parseInt(p.getProperty(key, "").trim());
            return v < min || v > max ? fallback : v;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Options are stored as {@code <position>|<value>} so that their order survives the (unordered) properties file. */
    private static Map<String, String> orderedOptions(Map<String, String> stored) {
        List<Map.Entry<String, String>> entries = new ArrayList<>(stored.entrySet());
        entries.sort((a, b) -> Integer.compare(position(a.getValue()), position(b.getValue())));
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : entries) {
            String v = e.getValue();
            out.put(e.getKey(), v.contains("|") ? v.substring(v.indexOf('|') + 1) : v);
        }
        return out;
    }

    private static int position(String stored) {
        int bar = stored.indexOf('|');
        try {
            return bar > 0 ? Integer.parseInt(stored.substring(0, bar)) : Integer.MAX_VALUE;
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    public void save(Path file) throws IOException {
        Properties p = new Properties();
        for (int i = 0; i < engines.size(); i++) {
            EngineConfig e = engines.get(i);
            p.setProperty("engine." + i + ".name", e.name());
            p.setProperty("engine." + i + ".command", e.command());
            int n = 0;
            for (Map.Entry<String, String> o : e.options().entrySet()) {
                p.setProperty("engine." + i + ".option." + o.getKey(), (n++) + "|" + o.getValue());
            }
        }
        p.setProperty("last.engine", lastSetup.engineName() == null ? "" : lastSetup.engineName());
        p.setProperty("last.side", lastSetup.humanSide() == Side.O ? "o" : "x");
        p.setProperty("last.timeControl", lastSetup.timeControl().format());
        p.setProperty("analysis.engine", analysisEngine == null ? "" : analysisEngine);
        p.setProperty("match.engineA", matchPrefs.engineA() == null ? "" : matchPrefs.engineA());
        p.setProperty("match.engineB", matchPrefs.engineB() == null ? "" : matchPrefs.engineB());
        p.setProperty("match.timeControl", matchPrefs.timeControl().format());
        p.setProperty("match.games", String.valueOf(matchPrefs.games()));
        p.setProperty("match.plies", String.valueOf(matchPrefs.plies()));
        p.setProperty("match.seed", String.valueOf(matchPrefs.seed()));
        p.setProperty("match.grace", String.valueOf(matchPrefs.graceMs()));
        p.setProperty("match.extraA", matchPrefs.extraA().replace("\r", "").replace("\n", "\\n"));
        p.setProperty("match.extraB", matchPrefs.extraB().replace("\r", "").replace("\n", "\\n"));
        p.setProperty("match.openingFile", matchPrefs.openingFile());
        Path dir = file.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "uai-board settings");
        }
    }
}
