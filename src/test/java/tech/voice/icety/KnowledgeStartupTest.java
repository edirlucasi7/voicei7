package tech.voice.icety;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.*;

class KnowledgeStartupTest extends PostgresTestSupport {
    @TempDir Path directory;

    @Test
    void startupIndexesOnceAndFailedReplacementPreservesPreviousCorpus() throws Exception {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.update("DELETE FROM vector_store");
        jdbc.update("DELETE FROM knowledge_source");
        var file = directory.resolve("rules.md");
        Files.writeString(file, "Endereço original da clínica: Rua das Flores, 100.");
        var requests = new AtomicInteger();
        var fail = new AtomicBoolean();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/embed", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            var vector = "[1" + ",0".repeat(767) + "]";
            var body = (fail.get() ? "{\"error\":\"Embedding failed\"}"
                    : "{\"model\":\"nomic-embed-text\",\"embeddings\":[" + vector + "]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(fail.get() ? 400 : 200, body.length);
            try (var response = exchange.getResponseBody()) {
                response.write(body);
            }
        });
        server.start();
        var url = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            try (var context = application(directory, true, url)) {
                assertThat(context.isActive()).isTrue();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM vector_store", Integer.class)).isEqualTo(1);
            }

            try (var context = application(directory, true, url)) {
                assertThat(context.isActive()).isTrue();
                assertThat(requests.get()).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM vector_store", Integer.class)).isEqualTo(1);
            }

            var previousHash = jdbc.queryForObject("SELECT content_hash FROM knowledge_source", String.class);
            Files.writeString(file, "Endereço atualizado da clínica: Avenida Brasil, 200.");
            fail.set(true);
            assertThatThrownBy(() -> application(directory, true, url)).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT content FROM vector_store", String.class)).contains("original");
            assertThat(jdbc.queryForObject("SELECT content_hash FROM knowledge_source", String.class))
                    .isEqualTo(previousHash);
            assertThatThrownBy(() -> application(directory.resolve("missing"), true, url))
                    .isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_source", Integer.class)).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void disabledStartupDoesNotReadTheDirectoryOrContactOllama() {
        try (var context = application(directory.resolve("missing"), false, "http://127.0.0.1:1")) {
            assertThat(context.isActive()).isTrue();
        }
    }

    private ConfigurableApplicationContext application(Path documents, boolean enabled, String ollamaUrl) {
        return new SpringApplicationBuilder(IcetyApplication.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--knowledge.directory=" + documents,
                "--knowledge.ingest-on-startup=" + enabled,
                "--spring.ai.ollama.base-url=" + ollamaUrl,
                "--spring.ai.ollama.embedding.options.model=nomic-embed-text",
                "--schedule.closed-days=SUNDAY", "--reservation.cleanup-enabled=false",
                "--logging.level.root=OFF", "--spring.main.banner-mode=off");
    }
}
