package spamalot.uaiboard.uai;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * An option an engine announced with an {@code option name ... type ...} line during the handshake.
 *
 * @param type {@code spin}, {@code check}, {@code combo}, {@code button} or {@code string}
 * @param defaultValue the default as the engine wrote it (empty if there is none; {@code <empty>} stays as written)
 * @param min lower bound of a spin option, or null
 * @param max upper bound of a spin option, or null
 * @param choices the values of a combo option
 */
public record UaiOption(String name, String type, String defaultValue, Long min, Long max, List<String> choices) {
    private static final Set<String> KEYWORDS = Set.of("name", "type", "default", "min", "max", "var");

    /** Parses one {@code option} line; names and values may contain spaces. Returns null if it is not an option line. */
    public static UaiOption parse(String line) {
        String[] t = line.trim().split("\\s+");
        if (t.length < 5 || !t[0].equals("option") || !t[1].equals("name")) {
            return null;
        }
        String name = null;
        String type = null;
        String def = "";
        Long min = null;
        Long max = null;
        List<String> choices = new ArrayList<>();
        int i = 1;
        while (i < t.length) {
            String key = t[i];
            if (!KEYWORDS.contains(key)) {
                i++;
                continue;
            }
            int j = i + 1;
            StringBuilder value = new StringBuilder();
            while (j < t.length && !KEYWORDS.contains(t[j])) {
                if (value.length() > 0) {
                    value.append(' ');
                }
                value.append(t[j]);
                j++;
            }
            String v = value.toString();
            switch (key) {
                case "name" -> name = v;
                case "type" -> type = v;
                case "default" -> def = v;
                case "min" -> min = parseLong(v);
                case "max" -> max = parseLong(v);
                default -> choices.add(v);
            }
            i = j;
        }
        if (name == null || name.isEmpty() || type == null) {
            return null;
        }
        return new UaiOption(name, type, def, min, max, List.copyOf(choices));
    }

    private static Long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
