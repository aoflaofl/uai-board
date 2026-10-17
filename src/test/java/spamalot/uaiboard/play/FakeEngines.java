package spamalot.uaiboard.play;

import java.util.List;
import java.util.Map;
import spamalot.uaiboard.uai.FakeEngine;

/** Configurations that start {@link FakeEngine} in a separate JVM. */
final class FakeEngines {
    private FakeEngines() {}

    static EngineConfig config(String mode) {
        return config(mode, Map.of());
    }

    static EngineConfig config(String mode, Map<String, String> options) {
        String java = System.getProperty("java.home") + "/bin/java";
        return new EngineConfig("fake-" + mode,
                CommandLine.join(List.of(java, "-cp", System.getProperty("java.class.path"), FakeEngine.class.getName(), mode)),
                options);
    }
}
