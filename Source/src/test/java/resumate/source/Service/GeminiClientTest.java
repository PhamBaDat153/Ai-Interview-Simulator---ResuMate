package resumate.source.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import resumate.source.DTO.EphemeralToken;
import resumate.source.DTO.EvaluationResponse;
import resumate.source.DTO.GeminiMessage;
import resumate.source.DTO.LiveTokenSpec;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiClientTest {

    private MockRestServiceServer server;
    private GeminiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiClient(builder, new ObjectMapper(),
                "test-key", "test-model", GeminiTestSupport.BASE_URL);
    }

    @Test
    void generateJsonSendsKeyInHeaderAndParsesResult() {
        server.expect(requestTo(GeminiTestSupport.BASE_URL
                        + "/v1beta/models/test-model:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("system"))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("hello"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andRespond(withSuccess(GeminiTestSupport.generateContentResponse(
                        "{\"evaluationScore\":80,\"summary\":\"ok\",\"strengths\":[\"a\"],"
                                + "\"weaknesses\":[],\"improvements\":[],\"extra\":1}"),
                        MediaType.APPLICATION_JSON));

        EvaluationResponse response = client.generateJson(
                "system", List.of(GeminiMessage.user("hello")), EvaluationResponse.class);

        assertThat(response.getEvaluationScore()).isEqualTo(80);
        assertThat(response.getStrengths()).containsExactly("a");
        server.verify();
    }

    @Test
    void generateJsonStripsCodeFence() {
        server.expect(requestTo(GeminiTestSupport.BASE_URL
                        + "/v1beta/models/test-model:generateContent"))
                .andRespond(withSuccess(GeminiTestSupport.generateContentResponse(
                        "```json\n{\"evaluationScore\":42}\n```"), MediaType.APPLICATION_JSON));

        EvaluationResponse response = client.generateJson(
                null, List.of(GeminiMessage.user("hello")), EvaluationResponse.class);

        assertThat(response.getEvaluationScore()).isEqualTo(42);
    }

    @Test
    void rateLimitBecomesFriendlyMessage() {
        server.expect(requestTo(GeminiTestSupport.BASE_URL
                        + "/v1beta/models/test-model:generateContent"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"quota\"}}"));

        assertThatThrownBy(() -> client.generateJson(
                null, List.of(GeminiMessage.user("hello")), EvaluationResponse.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("quá tải");
    }

    @Test
    void invalidJsonBecomesFormatError() {
        server.expect(requestTo(GeminiTestSupport.BASE_URL
                        + "/v1beta/models/test-model:generateContent"))
                .andRespond(withSuccess(GeminiTestSupport.generateContentResponse("not json"),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateJson(
                null, List.of(GeminiMessage.user("hello")), EvaluationResponse.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Gemini trả về dữ liệu không đúng định dạng");
    }

    @Test
    void missingApiKeyFailsWithoutCallingGemini() {
        GeminiClient noKeyClient = new GeminiClient(RestClient.builder(), new ObjectMapper(),
                "", "test-model", GeminiTestSupport.BASE_URL);

        assertThatThrownBy(() -> noKeyClient.generateJson(
                null, List.of(GeminiMessage.user("hello")), EvaluationResponse.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }

    @Test
    void createEphemeralTokenReturnsTokenName() {
        Instant expire = Instant.parse("2026-11-21T14:00:00.123Z");

        server.expect(requestTo(GeminiTestSupport.BASE_URL + "/v1beta/auth_tokens"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.uses").value(1))
                .andExpect(jsonPath("$.expireTime").value("2026-11-21T14:00:00Z"))
                .andExpect(jsonPath("$.bidiGenerateContentSetup.model").value("models/gemini-live"))
                .andExpect(jsonPath("$.fieldMask").value("model"))
                .andRespond(withSuccess("{\"name\":\"auth_tokens/abc\"}", MediaType.APPLICATION_JSON));

        EphemeralToken token = client.createEphemeralToken(LiveTokenSpec.builder()
                .uses(1)
                .expireTime(expire)
                .bidiGenerateContentSetup(Map.of("model", "models/gemini-live"))
                .fieldMask("model")
                .build());

        assertThat(token.getToken()).isEqualTo("auth_tokens/abc");
        assertThat(token.getExpiresAt()).isEqualTo(expire);
        server.verify();
    }
}
