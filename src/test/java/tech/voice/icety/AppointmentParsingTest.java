package tech.voice.icety;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tech.voice.icety.adapter.in.web.AppointmentController;
import tech.voice.icety.application.port.in.ParseAppointmentUseCase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.voice.icety.domain.model.UserIntent.*;
import static tech.voice.icety.domain.model.DayPeriod.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({AppointmentTestConfiguration.class, AppointmentParsingTest.HttpConfiguration.class})
class AppointmentParsingTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private String requestBody;
    private MockMvc mvc;

    @Autowired
    private AppointmentController controller;

    @Autowired
    private ParseAppointmentUseCase parser;

    @Autowired
    private MockOllama mock;

    record MockOllama(RestClient.Builder builder, MockRestServiceServer server) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class HttpConfiguration {
        @Bean
        MockOllama mockOllama() {
            var builder = RestClient.builder();
            return new MockOllama(builder, MockRestServiceServer.bindTo(builder).build());
        }

        @Bean
        OllamaApi ollamaApi(MockOllama mock) {
            return OllamaApi.builder().baseUrl("http://ollama.test")
                    .restClientBuilder(mock.builder()).build();
        }
    }

    @BeforeEach
    void reset() {
        mock.server().reset();
        requestBody = null;
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private void respondWith(String content) {
        String response = JSON.writeValueAsString(Map.of(
                "model", "qwen2.5:3b", "created_at", "2026-10-19T12:00:00Z",
                "message", Map.of("role", "assistant", "content", content), "done", true));
        mock.server().expect(requestTo("http://ollama.test/api/chat"))
                .andExpect(request -> requestBody = ((MockClientHttpRequest) request).getBodyAsString())
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    @Test
    void convertsTypedJsonAndSendsTemporalContextAndSchema() {
        respondWith("""
                {"customerName":"Carlos Alberto","requestedDate":"2026-10-20",
                 "specificTime":null,"period":"AFTERNOON","intent":"BOOK"}
                """);

        var result = parser.parse(AppointmentExamples.CARLOS);

        assertThat(result.customerName()).isEqualTo("Carlos Alberto");
        assertThat(result.requestedDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(result.specificTime()).isNull();
        assertThat(result.period()).isEqualTo(AFTERNOON);
        assertThat(result.intent()).isEqualTo(BOOK);
        mock.server().verify();
        JsonNode request = JSON.readTree(requestBody);
        assertThat(request.path("model").asText()).isEqualTo("qwen2.5:3b");
        assertThat(request.path("stream").asBoolean()).isFalse();
        assertThat(request.path("options").required("temperature").asDouble()).isZero();
        JsonNode properties = request.path("format").path("properties");
        assertThat(properties.has("requestedDate")).isTrue();
        for (String field : new String[] { "customerName", "requestedDate", "specificTime" }) {
            assertThat(properties.path(field).path("type").toString()).contains("string", "null");
        }
        assertThat(request.path("messages").get(0).path("content").asText())
                .contains("2026-10-19 09:00 -03:00", "America/Fortaleza",
                        "Hoje: 2026-10-19", "Amanhã: 2026-10-20", "Próxima terça-feira: 2026-10-20");
        assertThat(request.path("messages").get(1).path("content").asText())
                .startsWith(AppointmentExamples.CARLOS);
    }

    @Test
    void correctsModelPeriodFromSpecificTimeWithoutInventingName() {
        respondWith("""
                {"customerName":null,"requestedDate":"2026-10-20",
                 "specificTime":"10:00","period":"AFTERNOON",
                 "intent":"CHECK_AVAILABILITY"}
                """);

        var result = parser.parse(AppointmentExamples.AVAILABILITY);

        assertThat(result.customerName()).isNull();
        assertThat(result.requestedDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(result.specificTime()).isEqualTo("10:00");
        assertThat(result.period()).isEqualTo(MORNING);
        assertThat(result.intent()).isEqualTo(CHECK_AVAILABILITY);
        mock.server().verify();
    }

    @Test
    void rejectsBlankTextWithoutCallingModel() {
        for (String text : new String[] { null, "", "   " }) {
            assertThatThrownBy(() -> parser.parse(text))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }
        mock.server().verify();
        assertThat(requestBody).isNull();
    }

    @Test
    void recoversExplicitTimeOmittedByModelThroughHttpEndpoint() throws Exception {
        respondWith("""
                {"customerName":null,"requestedDate":"2026-10-20",
                 "specificTime":null,"period":"UNSPECIFIED",
                 "intent":"CHECK_AVAILABILITY"}
                """);

        mvc.perform(post("/api/appointments/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("text", AppointmentExamples.AVAILABILITY))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").isEmpty())
                .andExpect(jsonPath("$.requestedDate").value("2026-10-20"))
                .andExpect(jsonPath("$.specificTime").value("10:00"))
                .andExpect(jsonPath("$.period").value("MORNING"))
                .andExpect(jsonPath("$.intent").value("CHECK_AVAILABILITY"));
        mock.server().verify();
        assertThat(requestBody).contains(AppointmentExamples.AVAILABILITY);
    }

    @Test
    void rejectsInvalidHttpInputWithoutCallingModel() throws Exception {
        for (String body : new String[] { "{}", "{\"text\":null}", "{\"text\":\"\"}",
                "{\"text\":\"   \"}", "null", "{", "" }) {
            mvc.perform(post("/api/appointments/parse")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mock.server().verify();
        assertThat(requestBody).isNull();
    }

    @Test
    void propagatesInvalidResponseConversionFailure() {
        respondWith("this is not JSON");
        assertThatThrownBy(() -> parser.parse(AppointmentExamples.AVAILABILITY))
                .isInstanceOf(RuntimeException.class);
        mock.server().verify();
    }
}
