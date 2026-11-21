package resumate.source.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import resumate.source.DTO.EvaluationRequest;
import resumate.source.DTO.EvaluationResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EvaluateServiceImplementTest {

    private MockRestServiceServer server;
    private EvaluateServiceImplement service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        GeminiClient geminiClient = new GeminiClient(builder, new ObjectMapper(),
                "test-key", "test-model", GeminiTestSupport.BASE_URL);
        service = new EvaluateServiceImplement(geminiClient, new DocumentInputService());
    }

    @Test
    void evaluateSendsCvAndJdAndReturnsResult() {
        server.expect(requestTo(GeminiTestSupport.BASE_URL
                        + "/v1beta/models/test-model:generateContent"))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.contents[0].parts[0].text", containsString("CV-SAMPLE-123")))
                .andExpect(jsonPath("$.contents[0].parts[0].text", containsString("JD-SAMPLE-456")))
                .andRespond(withSuccess(GeminiTestSupport.generateContentResponse(
                        "{\"evaluationScore\":75,\"summary\":\"s\",\"strengths\":[],"
                                + "\"weaknesses\":[],\"improvements\":[]}"),
                        MediaType.APPLICATION_JSON));

        EvaluationResponse response = service.evaluate(EvaluationRequest.builder()
                .cvText("CV-SAMPLE-123")
                .jdText("JD-SAMPLE-456")
                .build());

        assertThat(response.getEvaluationScore()).isEqualTo(75);
        server.verify();
    }

    @Test
    void missingCvKeepsOriginalMessage() {
        assertThatThrownBy(() -> service.evaluate(EvaluationRequest.builder()
                .jdText("JD").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CV không được để trống");
    }

    @Test
    void missingJdKeepsOriginalMessage() {
        assertThatThrownBy(() -> service.evaluate(EvaluationRequest.builder()
                .cvText("CV").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Mô tả công việc không được để trống");
    }
}
