package spamalot.uaiboard.play;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits and joins engine command lines. Words are separated by spaces; single or double quotes group a word that contains
 * spaces. There are no escape characters, so a Windows path in quotes stays as it is.
 */
public final class CommandLine {
    private CommandLine() {}

    /** @throws IllegalArgumentException if a quote is not closed */
    public static List<String> split(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        boolean inWord = false;
        char quote = 0;
        for (char c : text.toCharArray()) {
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    word.append(c);
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
                inWord = true;
            } else if (Character.isWhitespace(c)) {
                if (inWord) {
                    words.add(word.toString());
                    word.setLength(0);
                    inWord = false;
                }
            } else {
                word.append(c);
                inWord = true;
            }
        }
        if (quote != 0) {
            throw new IllegalArgumentException("unclosed " + quote + " in: " + text);
        }
        if (inWord) {
            words.add(word.toString());
        }
        return words;
    }

    /** The inverse of {@link #split}: words that need it are put in double quotes (or single quotes if they hold a double quote). */
    public static String join(List<String> words) {
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            if (w.isEmpty() || w.chars().anyMatch(ch -> Character.isWhitespace(ch) || ch == '"' || ch == '\'')) {
                char q = w.indexOf('"') >= 0 ? '\'' : '"';
                sb.append(q).append(w).append(q);
            } else {
                sb.append(w);
            }
        }
        return sb.toString();
    }
}
