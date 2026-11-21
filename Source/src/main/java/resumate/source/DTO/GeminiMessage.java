package resumate.source.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Một lượt hội thoại gửi tới Gemini (role "user" hoặc "model").
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class GeminiMessage {
    private String role;

    // Không đưa nội dung vào toString để tránh vô tình ghi log CV/JD/transcript.
    @ToString.Exclude
    private String text;

    public static GeminiMessage user(String text) {
        return new GeminiMessage("user", text);
    }

    public static GeminiMessage model(String text) {
        return new GeminiMessage("model", text);
    }
}
