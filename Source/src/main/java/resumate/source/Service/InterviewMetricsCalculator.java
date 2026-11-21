package resumate.source.Service;

import org.springframework.stereotype.Component;
import resumate.source.Model.InterviewMetrics;
import resumate.source.Model.InterviewSession;
import resumate.source.Model.TranscriptTurn;
import resumate.source.Model.TurnRole;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Tính các chỉ số của buổi phỏng vấn từ transcript, không gọi AI.
 */
@Component
public class InterviewMetricsCalculator {

    public InterviewMetrics calculate(InterviewSession session) {
        synchronized (session) {
            List<TranscriptTurn> transcript = session.getTranscript();

            int candidateTurns = 0;
            int candidateWords = 0;
            int totalWords = 0;

            for (TranscriptTurn turn : transcript) {
                int words = countWords(turn.getText());
                totalWords += words;

                if (turn.getRole() == TurnRole.CANDIDATE) {
                    candidateTurns++;
                    candidateWords += words;
                }
            }

            return InterviewMetrics.builder()
                    .durationSeconds(durationSeconds(session))
                    .candidateTurns(candidateTurns)
                    .candidateWords(candidateWords)
                    .averageAnswerWords(candidateTurns == 0
                            ? 0
                            : Math.round((float) candidateWords / candidateTurns))
                    .speakingSharePercent(totalWords == 0
                            ? 0
                            : Math.round(candidateWords * 100f / totalWords))
                    .build();
        }
    }

    /**
     * Thời lượng từ lúc vào phòng tới lúc kết thúc (hoặc lượt nói cuối cùng).
     */
    private long durationSeconds(InterviewSession session) {
        Instant start = session.getStartedAt();

        if (start == null) {
            return 0;
        }

        Instant end = session.getEndedAt();

        if (end == null && !session.getTranscript().isEmpty()) {
            end = session.getTranscript().get(session.getTranscript().size() - 1).getTimestamp();
        }

        if (end == null || end.isBefore(start)) {
            return 0;
        }

        return Duration.between(start, end).getSeconds();
    }

    static int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        return text.trim().split("\\s+").length;
    }
}
