package resumate.source.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import resumate.source.DTO.LiveMessageResponse;
import resumate.source.Model.InterviewSession;
import resumate.source.Model.InterviewStatus;
import resumate.source.Model.TranscriptTurn;
import resumate.source.Model.TurnRole;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LiveInterviewTextServiceTest {

    private static final String URL = GeminiTestSupport.BASE_URL
            + "/v1beta/models/test-model:generateContent";

    private MockRestServiceServer server;
    private LiveInterviewTextService service;
    private InterviewSession session;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        GeminiClient geminiClient = new GeminiClient(builder, new ObjectMapper(),
                "test-key", "test-model", GeminiTestSupport.BASE_URL);
        service = new LiveInterviewTextService(geminiClient, new InterviewPromptBuilder());

        session = LiveInterviewTestData.textSession();
        session.setStartedAt(Instant.now());
        session.setStatus(InterviewStatus.IN_PROGRESS);
    }

    private String turn(String reply, String questionId, boolean ended) {
        return GeminiTestSupport.generateContentResponse(
                "{\"reply\":\"" + reply + "\",\"currentQuestionId\":\"" + questionId
                        + "\",\"ended\":" + ended + "}");
    }

    @Test
    void greetAddsInterviewerTurnAndProgress() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.contents.length()").value(1))
                .andExpect(jsonPath("$.contents[0].parts[0].text", containsString("[HỆ THỐNG]")))
                .andExpect(jsonPath("$.generationConfig.responseSchema.properties.reply.type").value("STRING"))
                .andRespond(withSuccess(turn("Chào bạn! Câu đầu tiên...", "q1", false),
                        MediaType.APPLICATION_JSON));

        service.greet(session);

        assertThat(session.getTranscript()).hasSize(1);
        assertThat(session.getTranscript().get(0).getRole()).isEqualTo(TurnRole.INTERVIEWER);
        assertThat(session.getCurrentQuestionId()).isEqualTo("q1");
        server.verify();
    }

    @Test
    void replyAlternatesRolesAndUpdatesProgress() {
        session.getTranscript().add(new TranscriptTurn(TurnRole.INTERVIEWER, "Câu 1?", Instant.now()));
        session.setCurrentQuestionId("q1");

        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.contents.length()").value(3))
                .andExpect(jsonPath("$.contents[1].role").value("model"))
                .andExpect(jsonPath("$.contents[2].role").value("user"))
                .andExpect(jsonPath("$.contents[2].parts[0].text").value("Em làm dự án ShopX"))
                .andExpect(jsonPath("$.contents[2].parts[0].text", not(containsString("Còn 1 phút"))))
                .andRespond(withSuccess(turn("Cảm ơn. Câu tiếp theo...", "q2", false),
                        MediaType.APPLICATION_JSON));

        LiveMessageResponse response = service.reply(session, "  Em làm dự án ShopX  ");

        assertThat(response.getReply()).isEqualTo("Cảm ơn. Câu tiếp theo...");
        assertThat(response.isEnded()).isFalse();
        assertThat(response.getProgress().getCurrent()).isEqualTo(2);
        assertThat(response.getProgress().getTotal()).isEqualTo(3);
        assertThat(session.getTranscript()).extracting(TranscriptTurn::getRole)
                .containsExactly(TurnRole.INTERVIEWER, TurnRole.CANDIDATE, TurnRole.INTERVIEWER);
    }

    @Test
    void endedReplyCompletesInterview() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(turn("Cảm ơn bạn, hẹn gặp lại!", "q3", true),
                        MediaType.APPLICATION_JSON));

        LiveMessageResponse response = service.reply(session, "Em xin hết ạ");

        assertThat(response.isEnded()).isTrue();
        assertThat(session.getStatus()).isEqualTo(InterviewStatus.COMPLETED);
        assertThat(session.getEndedAt()).isNotNull();
    }

    @Test
    void unknownQuestionIdDoesNotChangeProgress() {
        session.setCurrentQuestionId("q1");
        server.expect(requestTo(URL))
                .andRespond(withSuccess(turn("Ừm...", "q99", false), MediaType.APPLICATION_JSON));

        LiveMessageResponse response = service.reply(session, "Câu trả lời");

        assertThat(response.getProgress().getCurrentQuestionId()).isEqualTo("q1");
    }

    @Test
    void oneMinuteNoteIsSentNearTheEnd() {
        session.setStartedAt(Instant.now().minusSeconds(10 * 60 - 30));

        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.contents[-1].parts[0].text", containsString("Còn 1 phút")))
                .andRespond(withSuccess(turn("Cảm ơn bạn!", "q2", true), MediaType.APPLICATION_JSON));

        service.reply(session, "Câu trả lời");
        server.verify();
    }

    @Test
    void timeUpEndsWithoutCallingAi() {
        session.setStartedAt(Instant.now().minusSeconds(11 * 60));
        server.expect(never(), requestTo(URL));

        LiveMessageResponse response = service.reply(session, "Câu trả lời");

        assertThat(response.isEnded()).isTrue();
        assertThat(response.getReply()).isNull();
        assertThat(session.getStatus()).isEqualTo(InterviewStatus.COMPLETED);
        assertThat(session.getTranscript()).isEmpty();
        server.verify();
    }

    @Test
    void lastAllowedTurnForcesEnd() {
        int cap = LiveInterviewRules.candidateTurnCap(session.getConfig());

        for (int index = 0; index < cap - 1; index++) {
            session.getTranscript().add(new TranscriptTurn(TurnRole.CANDIDATE, "a" + index, Instant.now()));
            session.getTranscript().add(new TranscriptTurn(TurnRole.INTERVIEWER, "b" + index, Instant.now()));
        }

        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.contents[-1].parts[0].text", containsString("giới hạn số lượt")))
                .andRespond(withSuccess(turn("Cảm ơn bạn!", "q3", false), MediaType.APPLICATION_JSON));

        LiveMessageResponse response = service.reply(session, "Câu cuối");

        assertThat(response.isEnded()).isTrue();
        assertThat(session.getStatus()).isEqualTo(InterviewStatus.COMPLETED);
    }

    @Test
    void geminiFailureRollsBackCandidateTurn() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> service.reply(session, "Câu trả lời"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(session.getTranscript()).isEmpty();
    }

    @Test
    void blankAndTooLongMessagesAreRejected() {
        assertThatThrownBy(() -> service.reply(session, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Câu trả lời không được để trống");

        assertThatThrownBy(() -> service.reply(session, "a".repeat(4_001)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void messagesAfterEndAreRejected() {
        session.setStatus(InterviewStatus.COMPLETED);

        assertThatThrownBy(() -> service.reply(session, "Xin chào"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Buổi phỏng vấn đã kết thúc");
    }
}
