package resumate.source.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import resumate.source.DTO.EphemeralToken;
import resumate.source.DTO.LiveTokenResponse;
import resumate.source.DTO.LiveTokenSpec;
import resumate.source.Model.InterviewMode;
import resumate.source.Model.InterviewSession;
import resumate.source.Model.PlannedQuestion;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cấp ephemeral token cho phiên phỏng vấn bằng giọng nói.
 * Token bị khóa với model, system instruction, công cụ, transcription do máy chủ tạo,
 * nên trình duyệt không thể thay đổi vai trò của người phỏng vấn.
 */
@Service
public class LiveTokenService {

    private static final Logger log = LoggerFactory.getLogger(LiveTokenService.class);

    // Số lần cấp token tối đa cho một buổi phỏng vấn (lần đầu + các lần kết nối lại).
    public static final int MAX_MINTS = 5;

    // Thời gian để mở phiên mới bằng token sau khi được cấp.
    static final Duration NEW_SESSION_WINDOW = Duration.ofMinutes(1);

    // Token còn hiệu lực thêm sau thời lượng phỏng vấn (để kết thúc và kết nối lại).
    static final Duration EXPIRE_MARGIN = Duration.ofMinutes(5);

    private final GeminiClient geminiClient;
    private final InterviewPromptBuilder promptBuilder;
    private final String liveModel;
    private final String apiVersion;

    public LiveTokenService(GeminiClient geminiClient,
                            InterviewPromptBuilder promptBuilder,
                            @Value("${gemini.live-model}") String liveModel,
                            @Value("${gemini.live-api-version:v1beta}") String apiVersion) {
        this.geminiClient = geminiClient;
        this.promptBuilder = promptBuilder;
        this.liveModel = liveModel;
        this.apiVersion = apiVersion;
    }

    public LiveTokenResponse mint(InterviewSession session) {
        synchronized (session) {
            Instant now = Instant.now();

            if (session.getConfig().getMode() != InterviewMode.VOICE) {
                throw new IllegalStateException("Buổi phỏng vấn này không ở chế độ giọng nói");
            }

            if (LiveInterviewRules.isEnded(session)) {
                throw new IllegalStateException("Buổi phỏng vấn đã kết thúc");
            }

            if (session.getStartedAt() == null) {
                throw new IllegalStateException("Buổi phỏng vấn chưa bắt đầu");
            }

            if (LiveInterviewRules.remainingSeconds(session, now) <= 0) {
                throw new IllegalStateException("Đã hết thời gian phỏng vấn");
            }

            if (session.getTokenMints() >= MAX_MINTS) {
                throw new IllegalStateException(
                        "Đã vượt quá số lần kết nối cho phép. Vui lòng kết thúc buổi phỏng vấn.");
            }

            Instant expireTime = now
                    .plus(Duration.ofMinutes(session.getConfig().getDurationMinutes()))
                    .plus(EXPIRE_MARGIN);

            Map<String, Object> setup = buildSetup(session);

            EphemeralToken token = geminiClient.createEphemeralToken(LiveTokenSpec.builder()
                    .uses(1)
                    .expireTime(expireTime)
                    .newSessionExpireTime(now.plus(NEW_SESSION_WINDOW))
                    .bidiGenerateContentSetup(setup)
                    .fieldMask(fieldMask(setup))
                    .apiVersion(apiVersion)
                    .build());

            session.setTokenMints(session.getTokenMints() + 1);

            log.info("Đã cấp token giọng nói lần {}/{} cho buổi phỏng vấn {}",
                    session.getTokenMints(), MAX_MINTS, session.getId());

            return LiveTokenResponse.builder()
                    .token(token.getToken())
                    .model(liveModel)
                    .apiVersion(apiVersion)
                    .expiresAt(expireTime)
                    .remainingMints(MAX_MINTS - session.getTokenMints())
                    .build();
        }
    }

    /**
     * Cấu hình phiên Live theo định dạng BidiGenerateContentSetup.
     * Không đặt sessionResumption ở đây để trình duyệt tự truyền handle khi kết nối lại.
     */
    Map<String, Object> buildSetup(InterviewSession session) {

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseModalities", List.of("AUDIO"));

        if (session.getConfig().getVoiceName() != null) {
            generationConfig.put("speechConfig", Map.of(
                    "voiceConfig", Map.of(
                            "prebuiltVoiceConfig", Map.of(
                                    "voiceName", session.getConfig().getVoiceName()))));
        }

        Map<String, Object> setup = new LinkedHashMap<>();
        setup.put("model", "models/" + liveModel);
        setup.put("generationConfig", generationConfig);
        setup.put("systemInstruction", Map.of("parts", List.of(Map.of(
                "text", promptBuilder.build(session, InterviewPromptBuilder.Channel.VOICE)))));
        setup.put("tools", List.of(Map.of("functionDeclarations", functionDeclarations(session))));
        setup.put("inputAudioTranscription", Map.of());
        setup.put("outputAudioTranscription", Map.of());
        setup.put("contextWindowCompression", Map.of("slidingWindow", Map.of()));
        return setup;
    }

    /**
     * Khóa mọi trường đã đặt trong setup (cùng cách SDK chính thức tạo fieldMask).
     */
    String fieldMask(Map<String, Object> setup) {
        List<String> fields = new ArrayList<>();

        for (Map.Entry<String, Object> entry : setup.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> map && !map.isEmpty()) {
                map.keySet().forEach(key -> fields.add(entry.getKey() + "." + key));
            } else {
                fields.add(entry.getKey());
            }
        }

        return String.join(",", fields);
    }

    private List<Map<String, Object>> functionDeclarations(InterviewSession session) {

        List<String> questionIds = session.getPlan().getQuestions().stream()
                .map(PlannedQuestion::getId)
                .toList();

        Map<String, Object> markQuestion = Map.of(
                "name", "mark_question",
                "description", "Cập nhật tiến độ câu hỏi chính: gọi với status ASKED khi bắt đầu hỏi "
                        + "một câu hỏi chính, và COMPLETED khi ứng viên đã trả lời xong câu đó.",
                "parameters", Map.of(
                        "type", "OBJECT",
                        "properties", Map.of(
                                "questionId", Map.of(
                                        "type", "STRING",
                                        "description", "ID câu hỏi chính trong kế hoạch",
                                        "enum", questionIds),
                                "status", Map.of(
                                        "type", "STRING",
                                        "enum", List.of("ASKED", "COMPLETED"))),
                        "required", List.of("questionId", "status")));

        Map<String, Object> endInterview = Map.of(
                "name", "end_interview",
                "description", "Kết thúc buổi phỏng vấn, chỉ gọi sau khi đã nói lời cảm ơn và chào ứng viên.",
                "parameters", Map.of(
                        "type", "OBJECT",
                        "properties", Map.of(
                                "reason", Map.of(
                                        "type", "STRING",
                                        "description", "Lý do kết thúc ngắn gọn")),
                        "required", List.of("reason")));

        return List.of(markQuestion, endInterview);
    }
}
