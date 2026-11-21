package resumate.source.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Tạo phản hồi generateContent giả lập cho các bài test.
 */
final class GeminiTestSupport {

    static final String BASE_URL = "https://generativelanguage.googleapis.com";

    private GeminiTestSupport() {
    }

    static String generateContentResponse(String text) {
        try {
            return new ObjectMapper().writeValueAsString(Map.of(
                    "candidates", List.of(Map.of(
                            "content", Map.of(
                                    "role", "model",
                                    "parts", List.of(Map.of("text", text))),
                            "finishReason", "STOP"))));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
