package eventflow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

// The core must never import a plug-in.
public final class ArchitectureTest {

    private static final Path CORE = Path.of("src/main/java/eventflow/core");

    @Test
    public void coreNeverImportsAPlugin() throws IOException {
        Check.that(Files.isDirectory(CORE), "run the tests from the repository root");
        List<Path> offenders;
        try (Stream<Path> files = Files.walk(CORE)) {
            offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(ArchitectureTest::importsPlugin)
                    .toList();
        }
        Check.equal(List.of(), offenders, "core classes importing plugin/");
    }

    private static boolean importsPlugin(Path file) {
        try {
            return Files.readString(file).contains("import eventflow.plugin.");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
