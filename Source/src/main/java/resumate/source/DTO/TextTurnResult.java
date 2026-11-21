package resumate.source.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Kết quả JSON Gemini trả về cho mỗi lượt ở chế độ nhắn tin.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class TextTurnResult {
    @ToString.Exclude
    private String reply;
    private String currentQuestionId;
    private Boolean ended;
}
