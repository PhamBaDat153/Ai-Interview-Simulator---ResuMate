package resumate.source.Model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Một buổi phỏng vấn trực tiếp, được lưu trong bộ nhớ.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class InterviewSession {
    private String id;

    // ID của HttpSession đã tạo buổi phỏng vấn; chỉ session này được truy cập.
    @ToString.Exclude
    private String ownerSessionId;

    // Không đưa CV/JD/transcript vào toString để tránh ghi dữ liệu cá nhân ra log.
    @ToString.Exclude
    private String cvText;

    @ToString.Exclude
    private String jdText;

    private InterviewConfig config;

    @ToString.Exclude
    private InterviewPlan plan;

    private InterviewStatus status;

    @ToString.Exclude
    @Builder.Default
    private List<TranscriptTurn> transcript = new ArrayList<>();

    // ID câu hỏi chính đang được hỏi (q1, q2...), dùng để hiển thị tiến độ.
    private String currentQuestionId;

    // Báo cáo đánh giá, chỉ được tạo một lần rồi lưu lại.
    @ToString.Exclude
    private InterviewReport report;

    // Số lần đã cấp ephemeral token cho buổi phỏng vấn (giới hạn khi kết nối lại).
    private int tokenMints;

    private Instant createdAt;
    private Instant startedAt;
    private Instant endedAt;
}
