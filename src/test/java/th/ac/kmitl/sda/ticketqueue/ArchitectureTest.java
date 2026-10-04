package th.ac.kmitl.sda.ticketqueue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * NFR-08 · the structural fitness function.
 *
 * <p>The core system must never import a plug-in. If this fails, someone has
 * written {@code if (method.equals("stripe"))} somewhere in the core.
 */
public final class ArchitectureTest {

    private static final Path CORE = Path.of("src/main/java/th/ac/kmitl/sda/ticketqueue/core");

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
            return Files.readString(file).contains("import th.ac.kmitl.sda.ticketqueue.plugin.");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
