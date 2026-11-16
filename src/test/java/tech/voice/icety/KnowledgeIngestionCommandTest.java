package tech.voice.icety;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeIngestionCommandTest extends PostgresTestSupport {
    @TempDir Path temporary;

    @Test
    void ingestionCommandExitsWithoutOpeningHttpOrContactingOllama() throws Exception {
        var directory = Files.createDirectory(temporary.resolve("knowledge"));
        var log = temporary.resolve("ingestion.log");
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var process = new ProcessBuilder(java, "-cp", classpath, IcetyApplication.class.getName(),
                "--spring.profiles.active=ingest", "--knowledge.directory=" + directory,
                "--knowledge.ingest-on-startup=false", "--schedule.closed-days=SUNDAY",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.ai.ollama.base-url=http://127.0.0.1:1")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("Ingestion process should terminate").isTrue();
            var output = Files.readString(log);
            assertThat(process.exitValue()).as(output).isZero();
            assertThat(output).contains("Knowledge ingestion: updated=0, unchanged=0")
                    .contains("Shutdown completed").doesNotContain("Tomcat started");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
