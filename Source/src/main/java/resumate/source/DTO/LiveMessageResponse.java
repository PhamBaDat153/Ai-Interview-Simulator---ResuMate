package resumate.source.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Phản hồi của người phỏng vấn cho một tin nhắn (chế độ nhắn tin).
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class LiveMessageResponse {
    // Lời người phỏng vấn; null khi buổi phỏng vấn kết thúc mà không cần phản hồi thêm.
    @ToString.Exclude
    private String reply;
    private InterviewProgress progress;
    private boolean ended;
    private long remainingSeconds;
}
