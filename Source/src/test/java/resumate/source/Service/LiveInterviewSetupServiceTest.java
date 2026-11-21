package resumate.source.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import resumate.source.DTO.CreateInterviewSessionRequest;
import resumate.source.Model.InterviewPlan;
import resumate.source.Model.InterviewSession;
import resumate.source.Model.InterviewStatus;
import resumate.source.Model.PlannedQuestion;
import resumate.source.Repository.InterviewSessionRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LiveInterviewSetupServiceTest {

    private InterviewPlanService planService;
    private InterviewSessionRepository repository;
    private LiveInterviewSetupService service;

    @BeforeEach
    void setUp() {
        planService = mock(InterviewPlanService.class);
        repository = new InterviewSessionRepository();
        service = new LiveInterviewSetupService(new DocumentInputService(),
                new InterviewConfigMapper(), planService, repository);
    }

    @Test
    void createStoresSessionOwnedByHttpSession() {
        InterviewPlan plan = InterviewPlan.builder()
                .position("Dev")
                .questions(List.of(PlannedQuestion.builder().id("q1").question("Q1").build()))
                .build();
        when(planService.generate(eq("CV text"), eq("JD text"), any())).thenReturn(plan);

        CreateInterviewSessionRequest form = CreateInterviewSessionRequest.withDefaults();
        form.setCvText("  CV text  ");
        form.setJdText("JD text");

        InterviewSession session = service.create(form, "http-session-1");

        assertThat(session.getStatus()).isEqualTo(InterviewStatus.CREATED);
        assertThat(session.getPlan()).isSameAs(plan);
        assertThat(session.getTranscript()).isEmpty();
        assertThat(repository.findOwned(session.getId(), "http-session-1")).isPresent();
        assertThat(repository.findOwned(session.getId(), "someone-else")).isEmpty();
    }

    @Test
    void tooLongCvIsRejectedBeforeCallingAi() {
        CreateInterviewSessionRequest form = CreateInterviewSessionRequest.withDefaults();
        form.setCvText("a".repeat(LiveInterviewSetupService.MAX_DOCUMENT_CHARS + 1));
        form.setJdText("JD");

        assertThatThrownBy(() -> service.create(form, "s"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CV quá dài");
        verifyNoInteractions(planService);
    }

    @Test
    void invalidConfigIsRejectedBeforeReadingDocuments() {
        CreateInterviewSessionRequest form = CreateInterviewSessionRequest.withDefaults();
        form.setQuestionCount(2);

        assertThatThrownBy(() -> service.create(form, "s"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Số câu hỏi phải từ 3 đến 10");
        verifyNoInteractions(planService);
    }
}
