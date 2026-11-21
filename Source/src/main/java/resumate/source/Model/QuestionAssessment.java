package resumate.source.Model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Đánh giá câu trả lời cho một câu hỏi trong kế hoạch.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class QuestionAssessment {
    private String questionId;
    private String question;
    private String answerSummary;

    // Điểm câu hỏi (0-10).
    private int score;
    private String feedback;
    private List<String> missedPoints;
    private String sampleAnswer;
}
