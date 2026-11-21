package resumate.source.Service;

import org.junit.jupiter.api.Test;
import resumate.source.Model.InterviewMetrics;
import resumate.source.Model.InterviewSession;
import resumate.source.Model.TranscriptTurn;
import resumate.source.Model.TurnRole;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewMetricsCalculatorTest {

    private final InterviewMetricsCalculator calculator = new InterviewMetricsCalculator();

    @Test
    void metricsAreComputedFromTranscript() {
        Instant start = Instant.parse("2026-11-21T10:00:00Z");
        InterviewSession session = LiveInterviewTestData.textSession();
        session.setStartedAt(start);
        session.setEndedAt(start.plusSeconds(8 * 60 + 5));
        session.getTranscript().add(new TranscriptTurn(TurnRole.INTERVIEWER, "một hai ba bốn", start));
        session.getTranscript().add(new TranscriptTurn(TurnRole.CANDIDATE, "một  hai\nba", start.plusSeconds(10)));
        session.getTranscript().add(new TranscriptTurn(TurnRole.INTERVIEWER, "năm", start.plusSeconds(20)));
        session.getTranscript().add(new TranscriptTurn(TurnRole.CANDIDATE, "một hai ba bốn năm sáu bảy", start.plusSeconds(30)));

        InterviewMetrics metrics = calculator.calculate(session);

        assertThat(metrics.getDurationSeconds()).isEqualTo(485);
        assertThat(metrics.getDurationText()).isEqualTo("8:05");
        assertThat(metrics.getCandidateTurns()).isEqualTo(2);
        assertThat(metrics.getCandidateWords()).isEqualTo(10);
        assertThat(metrics.getAverageAnswerWords()).isEqualTo(5);
        assertThat(metrics.getSpeakingSharePercent()).isEqualTo(67);
    }

    @Test
    void emptyInterviewHasZeroMetrics() {
        InterviewMetrics metrics = calculator.calculate(LiveInterviewTestData.textSession());

        assertThat(metrics.getDurationSeconds()).isZero();
        assertThat(metrics.getCandidateTurns()).isZero();
        assertThat(metrics.getAverageAnswerWords()).isZero();
        assertThat(metrics.getSpeakingSharePercent()).isZero();
    }

    @Test
    void durationFallsBackToLastTurnWhenNotEnded() {
        Instant start = Instant.parse("2026-11-21T10:00:00Z");
        InterviewSession session = LiveInterviewTestData.textSession();
        session.setStartedAt(start);
        session.getTranscript().add(new TranscriptTurn(TurnRole.CANDIDATE, "xin chào", start.plusSeconds(90)));

        assertThat(calculator.calculate(session).getDurationSeconds()).isEqualTo(90);
    }
}
