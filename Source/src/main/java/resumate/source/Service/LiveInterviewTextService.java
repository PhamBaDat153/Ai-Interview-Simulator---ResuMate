package resumate.source.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import resumate.source.DTO.GeminiMessage;
import resumate.source.DTO.LiveMessageResponse;
import resumate.source.DTO.TextTurnResult;
import resumate.source.Model.InterviewLanguage;
import resumate.source.Model.InterviewMode;
import resumate.source.Model.InterviewSession;
import resumate.source.Model.TranscriptTurn;
import resumate.source.Model.TurnRole;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Xử lý từng lượt hội thoại ở chế độ nhắn tin: lưu lời ứng viên,
 * gọi Gemini với system instruction + toàn bộ lịch sử, lưu lời người phỏng vấn.
 */
@Service
public class LiveInterviewTextService {

    private static final Logger log = LoggerFactory.getLogger(LiveInterviewTextService.class);

    public static final int MAX_MESSAGE_CHARS = 4_000;

    // Khi còn ít hơn số giây này, nhắc người phỏng vấn kết thúc buổi phỏng vấn.
    static final long ONE_MINUTE_WARNING_SECONDS = 60;

    private final GeminiClient geminiClient;
    private final InterviewPromptBuilder promptBuilder;

    public LiveInterviewTextService(GeminiClient geminiClient, InterviewPromptBuilder promptBuilder) {
        this.geminiClient = geminiClient;
        this.promptBuilder = promptBuilder;
    }

    /**
     * Người phỏng vấn chào và hỏi câu đầu tiên (chỉ khi transcript còn trống).
     */
    public void greet(InterviewSession session) {
        synchronized (session) {
            if (!session.getTranscript().isEmpty() || LiveInterviewRules.isEnded(session)) {
                return;
            }

            TextTurnResult result = callModel(session, List.of());
            applyInterviewerTurn(session, result, false, Instant.now());
        }
    }

    /**
     * Ứng viên gửi một câu trả lời; trả về lời người phỏng vấn và tiến độ mới.
     */
    public LiveMessageResponse reply(InterviewSession session, String rawText) {

        String text = promptBuilder.stripSystemTags(rawText);

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Câu trả lời không được để trống");
        }

        if (text.length() > MAX_MESSAGE_CHARS) {
            throw new IllegalArgumentException(
                    "Câu trả lời quá dài, vui lòng rút gọn dưới 4.000 ký tự");
        }

        synchronized (session) {
            Instant now = Instant.now();

            if (session.getConfig().getMode() != InterviewMode.TEXT) {
                throw new IllegalStateException("Buổi phỏng vấn này không ở chế độ nhắn tin");
            }

            if (LiveInterviewRules.isEnded(session)) {
                throw new IllegalStateException("Buổi phỏng vấn đã kết thúc");
            }

            if (session.getStartedAt() == null) {
                throw new IllegalStateException("Buổi phỏng vấn chưa bắt đầu");
            }

            long remaining = LiveInterviewRules.remainingSeconds(session, now);
            long candidateTurns = LiveInterviewRules.candidateTurns(session);
            int turnCap = LiveInterviewRules.candidateTurnCap(session.getConfig());

            // Hết giờ hoặc hết lượt: kết thúc mà không gọi AI.
            if (remaining <= 0 || candidateTurns >= turnCap) {
                LiveInterviewRules.complete(session, now);
                return response(session, null, true, now);
            }

            session.getTranscript().add(TranscriptTurn.builder()
                    .role(TurnRole.CANDIDATE)
                    .text(text)
                    .timestamp(now)
                    .build());

            boolean lastTurn = candidateTurns + 1 >= turnCap;
            InterviewLanguage language = session.getConfig().getLanguage();
            List<String> notes = new ArrayList<>();

            if (remaining <= ONE_MINUTE_WARNING_SECONDS) {
                notes.add(promptBuilder.oneMinuteNote(language));
            }

            if (lastTurn) {
                notes.add(promptBuilder.turnLimitNote(language));
            }

            TextTurnResult result;

            try {
                result = callModel(session, notes);
            } catch (RuntimeException exception) {
                // Gọi AI lỗi: bỏ lượt vừa thêm để ứng viên có thể gửi lại.
                session.getTranscript().remove(session.getTranscript().size() - 1);
                throw exception;
            }

            return applyInterviewerTurn(session, result, lastTurn, Instant.now());
        }
    }

    private TextTurnResult callModel(InterviewSession session, List<String> notes) {

        TextTurnResult result = geminiClient.generateJson(
                promptBuilder.build(session, InterviewPromptBuilder.Channel.TEXT),
                buildContents(session, notes),
                turnSchema(),
                TextTurnResult.class);

        if (result == null || result.getReply() == null || result.getReply().isBlank()) {
            log.warn("Gemini trả về lượt phỏng vấn rỗng cho buổi {}", session.getId());
            throw new IllegalStateException(
                    "Người phỏng vấn AI chưa phản hồi được. Vui lòng gửi lại.");
        }

        return result;
    }

    private LiveMessageResponse applyInterviewerTurn(InterviewSession session,
                                                     TextTurnResult result,
                                                     boolean forceEnd,
                                                     Instant now) {
        String reply = result.getReply().trim();

        session.getTranscript().add(TranscriptTurn.builder()
                .role(TurnRole.INTERVIEWER)
                .text(reply)
                .timestamp(now)
                .build());

        if (LiveInterviewRules.isPlannedQuestion(session, result.getCurrentQuestionId())) {
            session.setCurrentQuestionId(result.getCurrentQuestionId());
        }

        boolean ended = Boolean.TRUE.equals(result.getEnded()) || forceEnd;

        if (ended) {
            LiveInterviewRules.complete(session, now);
        }

        return response(session, reply, ended, now);
    }

    /**
     * Lịch sử gửi cho Gemini: thông báo bắt đầu, các lượt trong transcript
     * (người phỏng vấn = "model", ứng viên = "user"), kèm thông báo hệ thống ở lượt cuối.
     */
    private List<GeminiMessage> buildContents(InterviewSession session, List<String> notes) {

        List<GeminiMessage> contents = new ArrayList<>();
        contents.add(GeminiMessage.user(promptBuilder.kickoffNote(session.getConfig().getLanguage())));

        for (TranscriptTurn turn : session.getTranscript()) {
            String role = turn.getRole() == TurnRole.INTERVIEWER ? "model" : "user";
            appendMerged(contents, role, turn.getText());
        }

        if (!notes.isEmpty()) {
            appendMerged(contents, "user", String.join("\n", notes));
        }

        return contents;
    }

    /**
     * Gộp các lượt liên tiếp cùng vai trò để hội thoại luôn xen kẽ user/model.
     */
    private void appendMerged(List<GeminiMessage> contents, String role, String text) {
        GeminiMessage last = contents.get(contents.size() - 1);

        if (last.getRole().equals(role)) {
            last.setText(last.getText() + "\n\n" + text);
        } else {
            contents.add(new GeminiMessage(role, text));
        }
    }

    private LiveMessageResponse response(InterviewSession session, String reply,
                                         boolean ended, Instant now) {
        return LiveMessageResponse.builder()
                .reply(reply)
                .progress(LiveInterviewRules.progress(session))
                .ended(ended)
                .remainingSeconds(LiveInterviewRules.remainingSeconds(session, now))
                .build();
    }

    private Map<String, Object> turnSchema() {
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "reply", Map.of("type", "STRING"),
                        "currentQuestionId", Map.of("type", "STRING"),
                        "ended", Map.of("type", "BOOLEAN")),
                "required", List.of("reply", "currentQuestionId", "ended"));
    }
}
