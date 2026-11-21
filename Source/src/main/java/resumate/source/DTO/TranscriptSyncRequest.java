package resumate.source.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import resumate.source.Model.TranscriptTurn;

import java.util.List;

/**
 * Toàn bộ transcript do trình duyệt gửi lên (chế độ giọng nói), thay thế bản đã lưu.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class TranscriptSyncRequest {
    @ToString.Exclude
    private List<TranscriptTurn> turns;
    private String currentQuestionId;
}
