package resumate.source.Model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cấu hình buổi phỏng vấn do người dùng chọn (đã được kiểm tra hợp lệ).
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class InterviewConfig {
    // Vị trí ứng tuyển; null nghĩa là để AI tự suy ra từ JD.
    private String targetPosition;
    private InterviewLevel level;
    private InterviewType interviewType;
    private InterviewDifficulty difficulty;
    private int questionCount;
    private int durationMinutes;
    private InterviewLanguage language;
    private InterviewPersona persona;
    private int maxFollowUps;
    private InterviewMode mode;
    private FeedbackTiming feedbackTiming;

    // Giọng đọc của Live API, chỉ dùng ở chế độ giọng nói; null = giọng mặc định.
    private String voiceName;
}
