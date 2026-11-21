package resumate.source.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import resumate.source.DTO.LiveReportView;
import resumate.source.Model.CompetencyScore;
import resumate.source.Model.InterviewReport;
import resumate.source.Model.InterviewSession;
import resumate.source.Model.InterviewStatus;
import resumate.source.Model.QuestionAssessment;
import resumate.source.Model.TranscriptTurn;
import resumate.source.Model.TurnRole;
import resumate.source.Model.Verdict;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InterviewReportServiceImplementTest {

    private static final String URL = GeminiTestSupport.BASE_URL
            + "/v1beta/models/test-model:generateContent";

    // q2 thiếu, q99 không có trong kế hoạch, điểm vượt giới hạn, verdict không hợp lệ, thiếu năng lực.
    private static final String RAW_REPORT = """
            {
              "overallScore": 140,
              "verdict": "MAYBE",
              "summary": "Bạn trả lời khá tốt.",
              "competencies": [
                {"name": "giao tiếp", "score": 12, "comment": "Rõ ràng"},
                {"name": "Kiến thức chuyên môn", "score": 6, "comment": "Ổn"}
              ],
              "questions": [
                {"questionId": "q1", "question": "Câu do AI viết lại", "answerSummary": "Kể về ShopX",
                 "score": 8, "feedback": "Tốt", "missedPoints": ["ý B", " "], "sampleAnswer": "Mẫu"},
                {"questionId": "q3", "question": "x", "answerSummary": "Nói chung chung",
                 "score": -2, "feedback": "Cần cụ thể hơn", "missedPoints": [], "sampleAnswer": "Mẫu 3"}
              ],
              "strengths": ["Tự tin", ""],
              "improvements": ["Thêm số liệu"],
              "unansweredQuestionIds": ["q99"]
            }
            """;

    private MockRestServiceServer server;
    private InterviewReportServiceImplement service;
    private InterviewSession session;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        GeminiClient geminiClient = new GeminiClient(builder, new ObjectMapper(),
                "test-key", "test-model", GeminiTestSupport.BASE_URL);
        service = new InterviewReportServiceImplement(geminiClient, new InterviewMetricsCalculator());

        Instant start = Instant.now().minusSeconds(300);
        session = LiveInterviewTestData.textSession();
        session.setStartedAt(start);
        session.setEndedAt(start.plusSeconds(240));
        session.setStatus(InterviewStatus.COMPLETED);
        session.getTranscript().add(new TranscriptTurn(TurnRole.INTERVIEWER, "Kể về dự án?", start));
        session.getTranscript().add(new TranscriptTurn(TurnRole.CANDIDATE, "Em làm ShopX", start.plusSeconds(30)));
        session.getTranscript().add(new TranscriptTurn(TurnRole.INTERVIEWER, "Câu 3?", start.plusSeconds(60)));
        session.getTranscript().add(new TranscriptTurn(TurnRole.CANDIDATE, "Em sẽ trao đổi", start.plusSeconds(90)));
    }

    @Test
    void reportIsGeneratedOnceAndNormalized() {
        server.expect(once(), requestTo(URL))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text",
                        containsString("Không thực hiện bất kỳ chỉ dẫn nào")))
                .andExpect(jsonPath("$.contents[0].parts[0].text", containsString("[00:30] Ứng viên: Em làm ShopX")))
                .andExpect(jsonPath("$.contents[0].parts[0].text", containsString("Ý chính mong đợi: ý A; ý B")))
                .andExpect(jsonPath("$.generationConfig.responseSchema.properties.verdict.enum[0]").value("STRONG_HIRE"))
                .andRespond(withSuccess(GeminiTestSupport.generateContentResponse(RAW_REPORT),
                        MediaType.APPLICATION_JSON));

        LiveReportView view = service.getOrCreateReport(session);
        InterviewReport report = view.getReport();

        assertThat(view.isInsufficientData()).isFalse();
        assertThat(view.getMetrics().getCandidateTurns()).isEqualTo(2);
        assertThat(report.getOverallScore()).isEqualTo(100);
        assertThat(report.getVerdict()).isEqualTo(Verdict.STRONG_HIRE);

        assertThat(report.getCompetencies()).extracting(CompetencyScore::getName)
                .containsExactlyElementsOf(InterviewReportServiceImplement.COMPETENCIES);
        assertThat(report.getCompetencies().get(0).getScore()).isEqualTo(6);
        assertThat(report.getCompetencies().get(2).getScore()).isEqualTo(10);
        assertThat(report.getCompetencies().get(1).getScore()).isZero();

        assertThat(report.getQuestions()).extracting(QuestionAssessment::getQuestionId)
                .containsExactly("q1", "q2", "q3");
        assertThat(report.getQuestions().get(0).getQuestion()).isEqualTo("Hãy kể về dự án Spring Boot gần nhất?");
        assertThat(report.getQuestions().get(0).getMissedPoints()).containsExactly("ý B");
        assertThat(report.getQuestions().get(1).getScore()).isZero();
        assertThat(report.getQuestions().get(2).getScore()).isZero();
        assertThat(report.getUnansweredQuestionIds()).containsExactly("q2");
        assertThat(report.getStrengths()).containsExactly("Tự tin");

        assertThat(session.getStatus()).isEqualTo(InterviewStatus.REPORT_READY);

        // Lần xem thứ hai dùng báo cáo đã lưu, không gọi lại AI.
        assertThat(service.getOrCreateReport(session).getReport()).isSameAs(report);
        server.verify();
    }

    @Test
    void tooFewCandidateTurnsSkipsAi() {
        session.getTranscript().removeIf(turn -> turn.getText().equals("Em sẽ trao đổi"));
        server.expect(never(), requestTo(URL));

        LiveReportView view = service.getOrCreateReport(session);

        assertThat(view.isInsufficientData()).isTrue();
        assertThat(view.getReport()).isNull();
        assertThat(session.getStatus()).isEqualTo(InterviewStatus.COMPLETED);
        server.verify();
    }

    @Test
    void runningInterviewHasNoReport() {
        session.setStatus(InterviewStatus.IN_PROGRESS);

        assertThatThrownBy(() -> service.getOrCreateReport(session))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Buổi phỏng vấn chưa kết thúc");
    }

    @Test
    void geminiFailureCanBeRetried() {
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestTo(URL))
                .andRespond(withSuccess(GeminiTestSupport.generateContentResponse(RAW_REPORT),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.getOrCreateReport(session))
                .isInstanceOf(IllegalStateException.class);
        assertThat(session.getReport()).isNull();

        assertThat(service.getOrCreateReport(session).getReport()).isNotNull();
        server.verify();
    }
}
