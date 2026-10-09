package th.ac.kmitl.sda.ticketqueue.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchitectureRuleTest {

    private static final Path CORE = Path.of("src/main/java/th/ac/kmitl/sda/ticketqueue/core");

    @Test
    void coreNeverImportsWebOrPlugin() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(CORE)) {
            offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String src = read(p);
                        return src.contains("import th.ac.kmitl.sda.ticketqueue.web")
                                || src.contains("import th.ac.kmitl.sda.ticketqueue.plugin");
                    })
                    .map(Path::toString)
                    .toList();
        }
        assertEquals(List.of(), offenders);
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
