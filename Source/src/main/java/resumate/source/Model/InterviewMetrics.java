package resumate.source.Model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Các chỉ số do máy chủ tự tính từ transcript (không dùng AI).
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class InterviewMetrics {
    // Thời lượng thực tế (giây).
    private long durationSeconds;
    private int candidateTurns;
    private int candidateWords;

    // Độ dài trung bình mỗi câu trả lời (số từ).
    private int averageAnswerWords;

    // Tỷ lệ lời nói của ứng viên trên tổng số từ của cả buổi (%).
    private int speakingSharePercent;

    /**
     * Thời lượng dạng mm:ss để hiển thị.
     */
    public String getDurationText() {
        return "%d:%02d".formatted(durationSeconds / 60, durationSeconds % 60);
    }
}
