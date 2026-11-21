package resumate.source.Model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Báo cáo đánh giá buổi phỏng vấn do Gemini tạo từ cấu hình, kế hoạch và transcript.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class InterviewReport {
    // Điểm tổng (0-100).
    private int overallScore;
    private Verdict verdict;
    private String summary;
    private List<CompetencyScore> competencies;
    private List<QuestionAssessment> questions;
    private List<String> strengths;
    private List<String> improvements;
    private List<String> unansweredQuestionIds;
}
