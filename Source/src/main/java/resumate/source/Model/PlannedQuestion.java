package resumate.source.Model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Một câu hỏi trong kế hoạch phỏng vấn (không bao giờ hiển thị trước cho ứng viên).
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class PlannedQuestion {
    private String id;
    private QuestionCategory category;
    private String question;

    // Mục đích đánh giá của câu hỏi (gắn với yêu cầu nào trong JD).
    private String purpose;

    // Các ý chính mà một câu trả lời tốt cần có.
    private List<String> expectedPoints;
    private InterviewDifficulty difficulty;
}
