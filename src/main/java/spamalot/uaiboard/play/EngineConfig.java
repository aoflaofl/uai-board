package spamalot.uaiboard.play;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An engine the user has set up: a name to show, the command line that starts it, and options to set after the handshake
 * (in the order given).
 */
public record EngineConfig(String name, String command, Map<String, String> options) {
    public EngineConfig {
        options = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }

    public EngineConfig(String name, String command) {
        this(name, command, Map.of());
    }

    /** The command split into the program and its arguments. */
    public List<String> commandLine() {
        return CommandLine.split(command);
    }

    /**
     * Parses option lines such as {@code Hash=64}, one per line, keeping their order. Blank lines and lines starting with
     * {@code #} are skipped; the name is everything before the first {@code =}.
     *
     * @throws IllegalArgumentException for a line without {@code =} or with an empty name
     */
    public static Map<String, String> parseOptions(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : text.split("\\R")) {
            String t = line.strip();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            int eq = t.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("not an option (expected Name=value): " + t);
            }
            out.put(t.substring(0, eq).strip(), t.substring(eq + 1).strip());
        }
        return out;
    }

    /** The options as {@link #parseOptions} reads them. */
    public String optionsText() {
        StringBuilder sb = new StringBuilder();
        options.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        return sb.toString();
    }
}
