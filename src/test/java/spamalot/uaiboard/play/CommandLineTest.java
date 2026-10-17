package spamalot.uaiboard.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommandLineTest {
    @Test
    void splitsOnSpacesAndKeepsQuotedWordsTogether() {
        assertEquals(List.of("java", "-jar", "/path with space/e.jar", "--x"),
                CommandLine.split("java -jar \"/path with space/e.jar\" --x"));
        assertEquals(List.of("a", "b c", "d"), CommandLine.split("  a   'b c'  d "));
        assertEquals(List.of("C:\\Program Files\\x.exe"), CommandLine.split("\"C:\\Program Files\\x.exe\""));
        assertEquals(List.of(), CommandLine.split("   "));
        assertEquals(List.of(""), CommandLine.split("\"\""));
    }

    @Test
    void unclosedQuoteIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> CommandLine.split("java \"-jar"));
    }

    @Test
    void joinIsTheInverseOfSplit() {
        List<String> words = List.of("java", "-jar", "/a b/c.jar", "it's", "say \"hi\"", "");
        assertEquals(words, CommandLine.split(CommandLine.join(words)));
    }
}
