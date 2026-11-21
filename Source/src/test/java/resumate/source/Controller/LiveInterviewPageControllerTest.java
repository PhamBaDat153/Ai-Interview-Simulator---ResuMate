package resumate.source.Controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import resumate.source.DTO.CreateInterviewSessionRequest;
import resumate.source.DTO.InterviewProgress;
import resumate.source.DTO.LiveReportView;
import resumate.source.DTO.LiveRoomState;
import resumate.source.Model.CompetencyScore;
import resumate.source.Model.InterviewMetrics;
import resumate.source.Model.InterviewReport;
import resumate.source.Model.QuestionAssessment;
import resumate.source.Model.Verdict;
import resumate.source.Model.InterviewConfig;
import resumate.source.Model.InterviewMode;
import resumate.source.Model.InterviewNotFoundException;
import resumate.source.Model.InterviewPlan;
import resumate.source.Model.InterviewSession;
import resumate.source.Model.InterviewStatus;
import resumate.source.Model.PlannedQuestion;
import resumate.source.Service.InterviewConfigMapper;
import resumate.source.Service.InterviewMetricsCalculator;
import resumate.source.Service.InterviewPromptBuilder;
import resumate.source.Service.InterviewReportService;
import resumate.source.Service.LiveInterviewSessionService;
import resumate.source.Service.LiveInterviewSetupService;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(LiveInterviewPageController.class)
class LiveInterviewPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LiveInterviewSetupService setupService;

    @MockitoBean
    private LiveInterviewSessionService sessionService;

    @MockitoBean
    private InterviewReportService reportService;

    @MockitoBean
    private InterviewMetricsCalculator metricsCalculator;

    @MockitoBean
    private InterviewPromptBuilder promptBuilder;

    private InterviewSession session(String id, String owner) {
        InterviewConfig config = new InterviewConfigMapper()
                .toConfig(CreateInterviewSessionRequest.withDefaults());

        return InterviewSession.builder()
                .id(id)
                .ownerSessionId(owner)
                .config(config)
                .plan(InterviewPlan.builder()
                        .position("Backend Developer")
                        .candidateSummary("Ứng viên có 2 năm Spring Boot")
                        .questions(List.of(PlannedQuestion.builder()
                                .id("q1").question("SECRET-QUESTION-TEXT").build()))
                        .build())
                .status(InterviewStatus.CREATED)
                .build();
    }

    @Test
    void setupPageRendersWithDefaults() throws Exception {
        mockMvc.perform(get("/interview/live/setup"))
                .andExpect(status().isOk())
                .andExpect(view().name("liveSetup"))
                .andExpect(model().attributeExists("form"))
                .andExpect(content().string(containsString("Thiết lập buổi phỏng vấn")));
    }

    @Test
    void validSubmitRedirectsToLobby() throws Exception {
        when(setupService.create(any(), anyString())).thenReturn(session("abc", "owner"));

        mockMvc.perform(multipart("/interview/live/setup")
                        .param("cvText", "CV")
                        .param("jdText", "JD")
                        .param("level", "JUNIOR")
                        .param("questionCount", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/interview/live/abc"));
    }

    @Test
    void serviceErrorReRendersFormWithUserInput() throws Exception {
        when(setupService.create(any(), anyString()))
                .thenThrow(new IllegalArgumentException("Mô tả công việc không được để trống"));

        mockMvc.perform(multipart("/interview/live/setup")
                        .param("cvText", "Kinh nghiệm Spring Boot 3 năm")
                        .param("targetPosition", "Java Developer"))
                .andExpect(status().isOk())
                .andExpect(view().name("liveSetup"))
                .andExpect(model().attribute("error", "Mô tả công việc không được để trống"))
                .andExpect(content().string(containsString("Kinh nghiệm Spring Boot 3 năm")))
                .andExpect(content().string(containsString("Java Developer")));
    }

    @Test
    void invalidEnumValueShowsErrorWithoutCallingService() throws Exception {
        mockMvc.perform(multipart("/interview/live/setup")
                        .param("cvText", "CV")
                        .param("level", "CEO"))
                .andExpect(status().isOk())
                .andExpect(view().name("liveSetup"))
                .andExpect(model().attribute("error", containsString("không hợp lệ")));

        verify(setupService, never()).create(any(), anyString());
    }

    @Test
    void uploadErrorParamShowsMessage() throws Exception {
        mockMvc.perform(get("/interview/live/setup").param("uploadError", ""))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", containsString("5MB")));
    }

    @Test
    void lobbyShowsSettingsButNeverQuestions() throws Exception {
        MockHttpSession httpSession = new MockHttpSession();
        when(sessionService.getOwned("abc", httpSession.getId()))
                .thenReturn(session("abc", httpSession.getId()));

        mockMvc.perform(get("/interview/live/abc").session(httpSession))
                .andExpect(status().isOk())
                .andExpect(view().name("liveLobby"))
                .andExpect(content().string(containsString("Backend Developer")))
                .andExpect(content().string(not(containsString("SECRET-QUESTION-TEXT"))));
    }

    @Test
    void lobbyOfAnotherSessionReturns404() throws Exception {
        when(sessionService.getOwned(eq("abc"), anyString()))
                .thenThrow(new InterviewNotFoundException());

        mockMvc.perform(get("/interview/live/abc"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("liveNotFound"));
    }

    @Test
    void textRoomRendersChatWithoutQuestions() throws Exception {
        InterviewSession session = session("abc", "owner");
        session.getConfig().setMode(InterviewMode.TEXT);
        when(sessionService.getOwned(eq("abc"), anyString())).thenReturn(session);

        mockMvc.perform(get("/interview/live/abc/room"))
                .andExpect(status().isOk())
                .andExpect(view().name("liveRoom"))
                .andExpect(content().string(containsString("data-chat-log")))
                .andExpect(content().string(containsString("/api/interview/live/abc")))
                .andExpect(content().string(not(containsString("SECRET-QUESTION-TEXT"))));
    }

    @Test
    void voiceRoomExposesQuestionIdsButNotQuestions() throws Exception {
        InterviewSession session = session("abc", "owner");
        when(sessionService.getOwned(eq("abc"), anyString())).thenReturn(session);
        when(promptBuilder.kickoffNote(any())).thenReturn("[HỆ THỐNG] KICKOFF");

        mockMvc.perform(get("/interview/live/abc/room"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-voice-start-button")))
                .andExpect(content().string(containsString("data-question-ids=\"q1\"")))
                .andExpect(content().string(containsString("KICKOFF")))
                .andExpect(content().string(containsString("/js/live-voice.js")))
                .andExpect(content().string(not(containsString("SECRET-QUESTION-TEXT"))));
    }

    @Test
    void endedRoomRedirectsToReport() throws Exception {
        InterviewSession session = session("abc", "owner");
        session.setStatus(InterviewStatus.COMPLETED);
        when(sessionService.getOwned(eq("abc"), anyString())).thenReturn(session);

        mockMvc.perform(get("/interview/live/abc/room"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/interview/live/abc/report"));
    }

    @Test
    void reportOfRunningInterviewRedirectsToRoom() throws Exception {
        InterviewSession session = session("abc", "owner");
        session.setStatus(InterviewStatus.IN_PROGRESS);
        when(sessionService.getOwned(eq("abc"), anyString())).thenReturn(session);

        mockMvc.perform(get("/interview/live/abc/report"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/interview/live/abc/room"));
    }

    private InterviewSession endedSession() {
        InterviewSession session = session("abc", "owner");
        session.setStatus(InterviewStatus.COMPLETED);
        when(sessionService.getOwned(eq("abc"), anyString())).thenReturn(session);
        when(sessionService.state(session)).thenReturn(LiveRoomState.builder()
                .turns(List.of())
                .progress(new InterviewProgress(1, 1, "q1"))
                .build());
        return session;
    }

    private InterviewMetrics metrics() {
        return InterviewMetrics.builder().durationSeconds(125).candidateTurns(4)
                .candidateWords(200).averageAnswerWords(50).speakingSharePercent(60).build();
    }

    @Test
    void reportShowsScoreVerdictAndQuestions() throws Exception {
        InterviewSession session = endedSession();
        InterviewReport report = InterviewReport.builder()
                .overallScore(78)
                .verdict(Verdict.HIRE)
                .summary("Bạn thể hiện tốt kiến thức Spring Boot.")
                .competencies(List.of(new CompetencyScore("Giao tiếp", 7, "Mạch lạc")))
                .questions(List.of(QuestionAssessment.builder()
                        .questionId("q1").question("SECRET-QUESTION-TEXT").answerSummary("Kể về ShopX")
                        .score(8).feedback("Tốt").missedPoints(List.of("Số liệu")).sampleAnswer("Mẫu")
                        .build()))
                .strengths(List.of("Tự tin"))
                .improvements(List.of("Thêm số liệu"))
                .unansweredQuestionIds(List.of())
                .build();
        when(reportService.getOrCreateReport(session)).thenReturn(new LiveReportView(report, metrics(), false));

        mockMvc.perform(get("/interview/live/abc/report"))
                .andExpect(status().isOk())
                .andExpect(view().name("liveReport"))
                .andExpect(content().string(containsString("78")))
                .andExpect(content().string(containsString("Nên tuyển")))
                .andExpect(content().string(containsString("verdict-hire")))
                .andExpect(content().string(containsString("2:05")))
                .andExpect(content().string(containsString("Tải PDF")))
                .andExpect(content().string(containsString("/interview/live/setup?from=abc")));
    }

    @Test
    void reportWithTooLittleDataShowsMessage() throws Exception {
        InterviewSession session = endedSession();
        when(reportService.getOrCreateReport(session)).thenReturn(new LiveReportView(null, metrics(), true));

        mockMvc.perform(get("/interview/live/abc/report"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chưa đủ dữ liệu để đánh giá")))
                .andExpect(content().string(not(containsString("Tải PDF"))));
    }

    @Test
    void reportFailureShowsErrorWithMetrics() throws Exception {
        InterviewSession session = endedSession();
        when(reportService.getOrCreateReport(session))
                .thenThrow(new IllegalStateException("Dịch vụ AI tạm thời không phản hồi. Vui lòng thử lại sau."));
        when(metricsCalculator.calculate(session)).thenReturn(metrics());

        mockMvc.perform(get("/interview/live/abc/report"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", containsString("không phản hồi")))
                .andExpect(content().string(containsString("Thử lại")));
    }

    @Test
    void setupFromPreviousInterviewPrefillsForm() throws Exception {
        InterviewSession session = session("abc", "owner");
        session.setCvText("CV-PREFILL-TEXT");
        session.setJdText("JD-PREFILL-TEXT");
        session.getConfig().setQuestionCount(7);
        when(sessionService.getOwned(eq("abc"), anyString())).thenReturn(session);

        mockMvc.perform(get("/interview/live/setup").param("from", "abc"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("form", org.hamcrest.Matchers.hasProperty("questionCount",
                        org.hamcrest.Matchers.is(7))))
                .andExpect(content().string(containsString("CV-PREFILL-TEXT")))
                .andExpect(content().string(containsString("JD-PREFILL-TEXT")));
    }

    @Test
    void setupFromUnknownInterviewFallsBackToDefaults() throws Exception {
        when(sessionService.getOwned(eq("zzz"), anyString())).thenThrow(new InterviewNotFoundException());

        mockMvc.perform(get("/interview/live/setup").param("from", "zzz"))
                .andExpect(status().isOk())
                .andExpect(view().name("liveSetup"))
                .andExpect(model().attribute("form", org.hamcrest.Matchers.hasProperty("questionCount",
                        org.hamcrest.Matchers.is(5))));
    }

    @Test
    void useTextSwitchesModeAndRedirectsToRoom() throws Exception {
        InterviewSession session = session("abc", "owner");
        when(sessionService.getOwned(eq("abc"), anyString())).thenReturn(session);

        mockMvc.perform(post("/interview/live/abc/use-text"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/interview/live/abc/room"));

        verify(sessionService).switchToText(session);
    }
}
