package eventflow.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** NOT a style check. The dependency rules of docs/06 §6.5, enforced by the build. */
class ArchitectureRuleTest {

    private static final Path ROOT = Path.of("src/main/java/eventflow");
    private static final String WEB = "eventflow.web";
    private static final String PLUGIN = "eventflow.plugin";

    @Test
    void onlyWebMayUseWeb() throws IOException {
        assertEquals(List.of(), offenders(p -> !p.startsWith(ROOT.resolve("web")), WEB));
    }

    @Test
    void coreNeverUsesPlugins() throws IOException {
        assertEquals(List.of(), offenders(p -> p.startsWith(ROOT.resolve("core")), PLUGIN));
    }

    private static List<String> offenders(java.util.function.Predicate<Path> scope, String forbidden) throws IOException {
        try (Stream<Path> files = Files.walk(ROOT)) {
            return files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(scope)
                    .filter(p -> read(p).contains(forbidden))
                    .map(Path::toString)
                    .toList();
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
