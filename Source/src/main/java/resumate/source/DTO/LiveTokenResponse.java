package resumate.source.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

/**
 * Ephemeral token trả về cho trình duyệt để kết nối trực tiếp tới Gemini Live API.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class LiveTokenResponse {
    // Không bao giờ in token ra log.
    @ToString.Exclude
    private String token;
    private String model;
    private String apiVersion;
    private Instant expiresAt;

    // Số lần còn được cấp token (dùng khi kết nối lại).
    private int remainingMints;
}
