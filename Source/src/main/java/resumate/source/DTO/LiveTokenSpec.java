package resumate.source.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Thông số để tạo ephemeral token cho Gemini Live API.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class LiveTokenSpec {
    // Số lần token được dùng để mở phiên mới.
    private int uses;

    // Thời điểm token hết hạn hoàn toàn.
    private Instant expireTime;

    // Hạn chót để bắt đầu một phiên Live mới bằng token.
    private Instant newSessionExpireTime;

    // Cấu hình phiên Live (định dạng BidiGenerateContentSetup) gắn vào token.
    private Map<String, Object> bidiGenerateContentSetup;

    // Danh sách trường bị khóa, phân tách bằng dấu phẩy (null = khóa toàn bộ).
    private String fieldMask;

    // Phiên bản API dùng để tạo token, mặc định v1beta.
    private String apiVersion;
}
