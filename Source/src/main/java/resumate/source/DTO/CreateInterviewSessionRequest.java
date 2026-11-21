package resumate.source.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.web.multipart.MultipartFile;
import resumate.source.Model.FeedbackTiming;
import resumate.source.Model.InterviewConfig;
import resumate.source.Model.InterviewDifficulty;
import resumate.source.Model.InterviewLanguage;
import resumate.source.Model.InterviewLevel;
import resumate.source.Model.InterviewMode;
import resumate.source.Model.InterviewPersona;
import resumate.source.Model.InterviewSession;
import resumate.source.Model.InterviewType;

/**
 * Dữ liệu người dùng gửi lên từ form thiết lập phỏng vấn trực tiếp.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CreateInterviewSessionRequest {
    @ToString.Exclude
    private String cvText;

    @ToString.Exclude
    private MultipartFile cvFile;

    @ToString.Exclude
    private String jdText;

    @ToString.Exclude
    private MultipartFile jdFile;

    private String targetPosition;
    private InterviewLevel level;
    private InterviewType interviewType;
    private InterviewDifficulty difficulty;
    private Integer questionCount;
    private Integer durationMinutes;
    private InterviewLanguage language;
    private InterviewPersona persona;
    private Integer maxFollowUps;
    private InterviewMode mode;
    private FeedbackTiming feedbackTiming;
    private String voiceName;

    /**
     * Form với các giá trị mặc định khi người dùng mở trang thiết lập lần đầu.
     */
    public static CreateInterviewSessionRequest withDefaults() {
        return CreateInterviewSessionRequest.builder()
                .level(InterviewLevel.JUNIOR)
                .interviewType(InterviewType.MIXED)
                .difficulty(InterviewDifficulty.MEDIUM)
                .questionCount(5)
                .durationMinutes(10)
                .language(InterviewLanguage.VI)
                .persona(InterviewPersona.FRIENDLY)
                .maxFollowUps(1)
                .mode(InterviewMode.VOICE)
                .feedbackTiming(FeedbackTiming.END_ONLY)
                .build();
    }

    /**
     * Form điền sẵn CV, JD và cấu hình của một buổi phỏng vấn trước ("Phỏng vấn lại với cấu hình này").
     */
    public static CreateInterviewSessionRequest fromSession(InterviewSession session) {
        InterviewConfig config = session.getConfig();

        return CreateInterviewSessionRequest.builder()
                .cvText(session.getCvText())
                .jdText(session.getJdText())
                .targetPosition(config.getTargetPosition())
                .level(config.getLevel())
                .interviewType(config.getInterviewType())
                .difficulty(config.getDifficulty())
                .questionCount(config.getQuestionCount())
                .durationMinutes(config.getDurationMinutes())
                .language(config.getLanguage())
                .persona(config.getPersona())
                .maxFollowUps(config.getMaxFollowUps())
                .mode(config.getMode())
                .feedbackTiming(config.getFeedbackTiming())
                .voiceName(config.getVoiceName())
                .build();
    }
}
