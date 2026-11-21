package resumate.source.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import resumate.source.DTO.LiveTokenResponse;
import resumate.source.Model.InterviewMode;
import resumate.source.Model.InterviewSession;
import resumate.source.Model.InterviewStatus;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LiveTokenServiceTest {

    private static final String TOKEN_URL = GeminiTestSupport.BASE_URL + "/v1beta/auth_tokens";

    private MockRestServiceServer server;
    private LiveTokenService service;
    private InterviewSession session;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        GeminiClient geminiClient = new GeminiClient(builder, new ObjectMapper(),
                "test-key", "test-model", GeminiTestSupport.BASE_URL);
        service = new LiveTokenService(geminiClient, new InterviewPromptBuilder(),
                "gemini-live-test", "v1beta");

        session = LiveInterviewTestData.textSession();
        session.getConfig().setMode(InterviewMode.VOICE);
        session.getConfig().setVoiceName("Kore");
        session.setStartedAt(Instant.now());
        session.setStatus(InterviewStatus.IN_PROGRESS);
    }

    @Test
    void mintLocksServerSideSetupExceptSessionResumption() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.uses").value(1))
                .andExpect(jsonPath("$.newSessionExpireTime").exists())
                .andExpect(jsonPath("$.bidiGenerateContentSetup.model").value("models/gemini-live-test"))
                .andExpect(jsonPath("$.bidiGenerateContentSetup.generationConfig.responseModalities[0]").value("AUDIO"))
                .andExpect(jsonPath("$.bidiGenerateContentSetup.generationConfig.speechConfig.voiceConfig.prebuiltVoiceConfig.voiceName").value("Kore"))
                .andExpect(jsonPath("$.bidiGenerateContentSetup.systemInstruction.parts[0].text", containsString("mark_question")))
                .andExpect(jsonPath("$.bidiGenerateContentSetup.tools[0].functionDeclarations[0].name").value("mark_question"))
                .andExpect(jsonPath("$.bidiGenerateContentSetup.tools[0].functionDeclarations[0].parameters.properties.questionId.enum[2]").value("q3"))
                .andExpect(jsonPath("$.bidiGenerateContentSetup.tools[0].functionDeclarations[1].name").value("end_interview"))
                .andExpect(jsonPath("$.bidiGenerateContentSetup.inputAudioTranscription").exists())
                .andExpect(jsonPath("$.bidiGenerateContentSetup.outputAudioTranscription").exists())
                .andExpect(jsonPath("$.bidiGenerateContentSetup.contextWindowCompression.slidingWindow").exists())
                .andExpect(jsonPath("$.bidiGenerateContentSetup.sessionResumption").doesNotExist())
                .andExpect(jsonPath("$.fieldMask", containsString("systemInstruction.parts")))
                .andExpect(jsonPath("$.fieldMask", containsString("tools")))
                .andExpect(jsonPath("$.fieldMask", containsString("generationConfig.speechConfig")))
                .andExpect(jsonPath("$.fieldMask", not(containsString("sessionResumption"))))
                .andRespond(withSuccess("{\"name\":\"auth_tokens/secret\"}", MediaType.APPLICATION_JSON));

        LiveTokenResponse response = service.mint(session);

        assertThat(response.getToken()).isEqualTo("auth_tokens/secret");
        assertThat(response.getModel()).isEqualTo("gemini-live-test");
        assertThat(response.getApiVersion()).isEqualTo("v1beta");
        assertThat(response.getRemainingMints()).isEqualTo(LiveTokenService.MAX_MINTS - 1);
        assertThat(response.getExpiresAt())
                .isBetween(Instant.now().plus(Duration.ofMinutes(14)), Instant.now().plus(Duration.ofMinutes(16)));
        assertThat(session.getTokenMints()).isEqualTo(1);
        assertThat(response.toString()).doesNotContain("auth_tokens/secret");
        server.verify();
    }

    @Test
    void fieldMaskMatchesOfficialSdkFormat() {
        String mask = service.fieldMask(service.buildSetup(session));

        assertThat(mask).isEqualTo("model,generationConfig.responseModalities,generationConfig.speechConfig,"
                + "systemInstruction.parts,tools,inputAudioTranscription,outputAudioTranscription,"
                + "contextWindowCompression.slidingWindow");
    }

    @Test
    void mintLimitIsEnforced() {
        server.expect(times(LiveTokenService.MAX_MINTS), requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"name\":\"auth_tokens/t\"}", MediaType.APPLICATION_JSON));

        for (int i = 0; i < LiveTokenService.MAX_MINTS; i++) {
            service.mint(session);
        }

        assertThatThrownBy(() -> service.mint(session))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vượt quá số lần kết nối");
        server.verify();
    }

    @Test
    void textModeCannotMintTokens() {
        session.getConfig().setMode(InterviewMode.TEXT);
        server.expect(never(), requestTo(TOKEN_URL));

        assertThatThrownBy(() -> service.mint(session))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Buổi phỏng vấn này không ở chế độ giọng nói");
    }

    @Test
    void endedOrNotStartedInterviewCannotMintTokens() {
        session.setStartedAt(null);
        assertThatThrownBy(() -> service.mint(session)).isInstanceOf(IllegalStateException.class);

        session.setStartedAt(Instant.now());
        session.setStatus(InterviewStatus.COMPLETED);
        assertThatThrownBy(() -> service.mint(session))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Buổi phỏng vấn đã kết thúc");
    }

    @Test
    void failedMintDoesNotConsumeQuota() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.mint(session)).isInstanceOf(IllegalStateException.class);
        assertThat(session.getTokenMints()).isZero();
    }
}
