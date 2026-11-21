package resumate.source.Model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

/**
 * Một lượt nói trong buổi phỏng vấn.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class TranscriptTurn {
    private TurnRole role;

    // Không đưa nội dung vào toString để tránh ghi transcript ra log.
    @ToString.Exclude
    private String text;
    private Instant timestamp;
}
