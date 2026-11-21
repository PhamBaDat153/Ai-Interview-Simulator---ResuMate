package resumate.source.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import resumate.source.DTO.LiveRoomState;
import resumate.source.DTO.TranscriptSyncRequest;
import resumate.source.Model.InterviewMode;
import resumate.source.Model.InterviewNotFoundException;
import resumate.source.Model.InterviewSession;
import resumate.source.Model.InterviewStatus;
import resumate.source.Model.TranscriptTurn;
import resumate.source.Model.TurnRole;
import resumate.source.Repository.InterviewSessionRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class LiveInterviewSessionServiceTest {

    private InterviewSessionRepository repository;
    private LiveInterviewTextService textService;
    private LiveInterviewSessionService service;
    private InterviewSession session;

    @BeforeEach
    void setUp() {
        repository = new InterviewSessionRepository();
        textService = mock(LiveInterviewTextService.class);
        service = new LiveInterviewSessionService(repository, textService);
        session = repository.save(LiveInterviewTestData.textSession());
    }

    @Test
    void getOwnedRejectsOtherHttpSessions() {
        assertThat(service.getOwned("session-1", "owner")).isSameAs(session);
        assertThatThrownBy(() -> service.getOwned("session-1", "intruder"))
                .isInstanceOf(InterviewNotFoundException.class);
    }

    @Test
    void startBeginsClockOnceAndGreetsInTextMode() {
        LiveRoomState state = service.start(session);
        Instant startedAt = session.getStartedAt();

        assertThat(session.getStatus()).isEqualTo(InterviewStatus.IN_PROGRESS);
        assertThat(state.getRemainingSeconds()).isBetween(599L, 600L);
        assertThat(state.isEnded()).isFalse();
        verify(textService).greet(session);

        service.start(session);
        assertThat(session.getStartedAt()).isEqualTo(startedAt);
    }

    @Test
    void startInVoiceModeDoesNotCallTextModel() {
        session.getConfig().setMode(InterviewMode.VOICE);

        service.start(session);

        verify(textService, never()).greet(session);
    }

    @Test
    void finishIsIdempotent() {
        service.finish(session);
        Instant endedAt = session.getEndedAt();
        service.finish(session);

        assertThat(session.getStatus()).isEqualTo(InterviewStatus.COMPLETED);
        assertThat(session.getEndedAt()).isEqualTo(endedAt);
    }

    @Test
    void voiceTranscriptReplacesStoredTurns() {
        session.getConfig().setMode(InterviewMode.VOICE);

        service.syncTranscript(session, TranscriptSyncRequest.builder()
                .turns(List.of(
                        new TranscriptTurn(TurnRole.INTERVIEWER, "Xin chào", null),
                        new TranscriptTurn(TurnRole.CANDIDATE, "  ", null),
                        new TranscriptTurn(TurnRole.CANDIDATE, "Chào anh", Instant.now())))
                .currentQuestionId("q2")
                .build());

        assertThat(session.getTranscript()).hasSize(2);
        assertThat(session.getTranscript().get(0).getTimestamp()).isNotNull();
        assertThat(session.getCurrentQuestionId()).isEqualTo("q2");
    }

    @Test
    void textTranscriptCannotBeOverwritten() {
        assertThatThrownBy(() -> service.syncTranscript(session, TranscriptSyncRequest.builder()
                .turns(List.of()).build()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void switchToTextClearsVoice() {
        session.getConfig().setMode(InterviewMode.VOICE);
        session.getConfig().setVoiceName("Kore");

        service.switchToText(session);

        assertThat(session.getConfig().getMode()).isEqualTo(InterviewMode.TEXT);
        assertThat(session.getConfig().getVoiceName()).isNull();
    }
}
