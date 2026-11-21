package resumate.source.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import resumate.source.DTO.EphemeralToken;
import resumate.source.DTO.GeminiMessage;
import resumate.source.DTO.LiveTokenSpec;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lớp duy nhất gọi Gemini REST API của hệ thống.
 * Khóa API chỉ được gửi qua header x-goog-api-key và không bao giờ được ghi log.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final String INVALID_FORMAT_MESSAGE =
            "Gemini trả về dữ liệu không đúng định dạng";

    private final RestClient client;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;

    public GeminiClient(
            RestClient.Builder builder,
            ObjectMapper mapper,
            @Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.model}") String model,
            @Value("${gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl) {

        // Dùng bản sao của builder để không làm thay đổi builder dùng chung trong ứng dụng.
        this.client = builder.clone()
                .baseUrl(baseUrl)
                .build();
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * Gọi generateContent với kết quả trả về dạng JSON và chuyển thành đối tượng kiểu T.
     */
    public <T> T generateJson(String systemInstruction,
                              List<GeminiMessage> contents,
                              Class<T> type) {
        return generateJson(systemInstruction, contents, null, type);
    }

    /**
     * Giống hàm trên nhưng cho phép truyền responseSchema để Gemini trả JSON đúng cấu trúc.
     */
    public <T> T generateJson(String systemInstruction,
                              List<GeminiMessage> contents,
                              Map<String, Object> responseSchema,
                              Class<T> type) {

        if (contents == null || contents.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nội dung gửi tới Gemini không được để trống");
        }

        // Tạo request body theo định dạng của generateContent.
        Map<String, Object> body = new LinkedHashMap<>();

        if (systemInstruction != null && !systemInstruction.isBlank()) {
            body.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", systemInstruction))));
        }

        body.put("contents", contents.stream()
                .map(message -> Map.of(
                        "role", message.getRole() == null ? "user" : message.getRole(),
                        "parts", List.of(Map.of(
                                "text", message.getText() == null ? "" : message.getText()))))
                .toList());

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseMimeType", "application/json");

        if (responseSchema != null) {
            generationConfig.put("responseSchema", responseSchema);
        }

        body.put("generationConfig", generationConfig);

        String raw = post("/v1beta/models/{model}:generateContent", body, model);
        String json = extractText(raw);

        try {
            // Bỏ qua trường lạ và giá trị enum không hợp lệ để phía service tự chuẩn hóa.
            return mapper.readerFor(type)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                    .readValue(json);
        } catch (Exception exception) {
            log.warn("Không thể chuyển JSON của Gemini thành {}: {}",
                    type.getSimpleName(), exception.getClass().getSimpleName());
            throw new IllegalStateException(INVALID_FORMAT_MESSAGE, exception);
        }
    }

    /**
     * Tạo ephemeral token cho Gemini Live API (chỉ dùng được với v1beta).
     */
    public EphemeralToken createEphemeralToken(LiveTokenSpec spec) {

        if (spec == null) {
            throw new IllegalArgumentException(
                    "Thông số tạo token không được để trống");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uses", spec.getUses());

        if (spec.getExpireTime() != null) {
            body.put("expireTime", formatTime(spec.getExpireTime()));
        }

        if (spec.getNewSessionExpireTime() != null) {
            body.put("newSessionExpireTime", formatTime(spec.getNewSessionExpireTime()));
        }

        if (spec.getBidiGenerateContentSetup() != null) {
            body.put("bidiGenerateContentSetup", spec.getBidiGenerateContentSetup());
        }

        if (spec.getFieldMask() != null && !spec.getFieldMask().isBlank()) {
            body.put("fieldMask", spec.getFieldMask());
        }

        String apiVersion = spec.getApiVersion() == null || spec.getApiVersion().isBlank()
                ? "v1beta"
                : spec.getApiVersion();

        String raw = post("/{version}/auth_tokens", body, apiVersion);

        try {
            // Giá trị token nằm trong trường "name" của phản hồi.
            String token = mapper.readTree(raw == null ? "" : raw).path("name").asText("");

            if (token.isBlank()) {
                throw new IllegalStateException("Phản hồi không chứa token");
            }

            return EphemeralToken.builder()
                    .token(token)
                    .expiresAt(spec.getExpireTime())
                    .build();
        } catch (Exception exception) {
            log.warn("Không thể đọc ephemeral token từ Gemini: {}",
                    exception.getClass().getSimpleName());
            throw new IllegalStateException(
                    "Không thể tạo phiên kết nối giọng nói. Vui lòng thử lại.",
                    exception);
        }
    }

    /**
     * Gửi request POST tới Gemini và chuyển các lỗi mạng/HTTP thành thông báo thân thiện.
     */
    private String post(String uri, Object body, Object... uriVariables) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Hệ thống chưa được cấu hình khóa Gemini API (GEMINI_API_KEY)");
        }

        byte[] payload;

        try {
            payload = mapper.writeValueAsBytes(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Không thể tạo yêu cầu gửi tới Gemini", exception);
        }

        try {
            return client.post()
                    .uri(uri, uriVariables)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();

            // Chỉ ghi mã lỗi và thông điệp lỗi của Google, không ghi nội dung đã gửi.
            log.warn("Gemini trả về lỗi HTTP {} cho {}: {}",
                    status, uri, readErrorMessage(exception.getResponseBodyAsString()));

            if (status == 429) {
                throw new IllegalStateException(
                        "Dịch vụ AI đang quá tải. Vui lòng thử lại sau ít phút.",
                        exception);
            }

            throw new IllegalStateException(
                    "Dịch vụ AI tạm thời không phản hồi. Vui lòng thử lại sau.",
                    exception);

        } catch (RestClientException exception) {
            log.warn("Không thể kết nối tới Gemini ({}): {}",
                    uri, exception.getClass().getSimpleName());
            throw new IllegalStateException(
                    "Không thể kết nối tới dịch vụ AI. Vui lòng kiểm tra mạng và thử lại.",
                    exception);
        }
    }

    /**
     * Lấy phần văn bản do model tạo ra trong phản hồi generateContent.
     */
    private String extractText(String raw) {

        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Gemini không trả về dữ liệu");
        }

        JsonNode response;

        try {
            response = mapper.readTree(raw);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(INVALID_FORMAT_MESSAGE, exception);
        }

        // Ghép các phần văn bản, bỏ qua phần "thought" (nếu có).
        StringBuilder text = new StringBuilder();
        JsonNode parts = response.at("/candidates/0/content/parts");

        if (parts.isArray()) {
            for (JsonNode part : parts) {
                if (part.path("thought").asBoolean(false)) {
                    continue;
                }

                JsonNode textNode = part.get("text");

                if (textNode != null && textNode.isTextual()) {
                    text.append(textNode.asText());
                }
            }
        }

        if (text.isEmpty()) {
            String reason = response.at("/candidates/0/finishReason")
                    .asText(response.at("/promptFeedback/blockReason").asText("UNKNOWN"));
            log.warn("Gemini không trả về nội dung văn bản (lý do: {})", reason);
            throw new IllegalStateException(
                    "Gemini không trả về nội dung hợp lệ. Vui lòng thử lại.");
        }

        return stripCodeFence(text.toString().trim());
    }

    /**
     * Phòng trường hợp model bọc JSON trong ```json ... ```.
     */
    private String stripCodeFence(String text) {

        if (!text.startsWith("```")) {
            return text;
        }

        int firstLineEnd = text.indexOf('\n');
        int lastFence = text.lastIndexOf("```");

        if (firstLineEnd < 0 || lastFence <= firstLineEnd) {
            return text;
        }

        return text.substring(firstLineEnd + 1, lastFence).trim();
    }

    /**
     * Đọc thông điệp lỗi ngắn gọn từ phản hồi lỗi của Google.
     */
    private String readErrorMessage(String body) {

        try {
            String message = mapper.readTree(body == null ? "" : body)
                    .path("error").path("message").asText("");

            return message.length() > 300 ? message.substring(0, 300) + "..." : message;
        } catch (Exception exception) {
            return "";
        }
    }

    private String formatTime(Instant instant) {
        // Định dạng UTC dạng YYYY-MM-DDTHH:MM:SSZ theo yêu cầu của API.
        return instant.truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
