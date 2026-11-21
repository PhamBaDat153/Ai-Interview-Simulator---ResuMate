package resumate.source.Service;

import org.springframework.stereotype.Component;
import resumate.source.DTO.CreateInterviewSessionRequest;
import resumate.source.Model.InterviewConfig;
import resumate.source.Model.InterviewMode;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Kiểm tra cấu hình người dùng nhập và chuyển thành InterviewConfig.
 */
@Component
public class InterviewConfigMapper {

    public static final int MIN_QUESTIONS = 3;
    public static final int MAX_QUESTIONS = 10;
    public static final List<Integer> DURATIONS = List.of(5, 10, 15);
    public static final int MAX_FOLLOW_UPS = 2;
    public static final int MAX_POSITION_LENGTH = 120;

    // Tên giọng đọc của Live API chỉ gồm chữ cái (ví dụ: Kore, Puck).
    private static final Pattern VOICE_NAME = Pattern.compile("[A-Za-z]{2,30}");

    public InterviewConfig toConfig(CreateInterviewSessionRequest form) {

        if (form == null) {
            throw new IllegalArgumentException(
                    "Yêu cầu thiết lập phỏng vấn không được để trống");
        }

        String position = trimToNull(form.getTargetPosition());

        if (position != null && position.length() > MAX_POSITION_LENGTH) {
            throw new IllegalArgumentException(
                    "Vị trí ứng tuyển tối đa " + MAX_POSITION_LENGTH + " ký tự");
        }

        require(form.getLevel(), "Vui lòng chọn cấp độ ứng viên");
        require(form.getInterviewType(), "Vui lòng chọn loại phỏng vấn");
        require(form.getDifficulty(), "Vui lòng chọn độ khó");
        require(form.getLanguage(), "Vui lòng chọn ngôn ngữ phỏng vấn");
        require(form.getPersona(), "Vui lòng chọn phong cách người phỏng vấn");
        require(form.getMode(), "Vui lòng chọn hình thức phỏng vấn");
        require(form.getFeedbackTiming(), "Vui lòng chọn thời điểm nhận xét");

        Integer questionCount = form.getQuestionCount();

        if (questionCount == null
                || questionCount < MIN_QUESTIONS
                || questionCount > MAX_QUESTIONS) {
            throw new IllegalArgumentException(
                    "Số câu hỏi phải từ " + MIN_QUESTIONS + " đến " + MAX_QUESTIONS);
        }

        Integer duration = form.getDurationMinutes();

        if (duration == null || !DURATIONS.contains(duration)) {
            throw new IllegalArgumentException(
                    "Thời lượng phỏng vấn chỉ có thể là 5, 10 hoặc 15 phút");
        }

        Integer maxFollowUps = form.getMaxFollowUps();

        if (maxFollowUps == null || maxFollowUps < 0 || maxFollowUps > MAX_FOLLOW_UPS) {
            throw new IllegalArgumentException(
                    "Số câu hỏi đào sâu tối đa phải từ 0 đến " + MAX_FOLLOW_UPS);
        }

        // Giọng đọc chỉ có ý nghĩa ở chế độ giọng nói.
        String voiceName = form.getMode() == InterviewMode.VOICE
                ? trimToNull(form.getVoiceName())
                : null;

        if (voiceName != null && !VOICE_NAME.matcher(voiceName).matches()) {
            throw new IllegalArgumentException("Giọng đọc không hợp lệ");
        }

        return InterviewConfig.builder()
                .targetPosition(position)
                .level(form.getLevel())
                .interviewType(form.getInterviewType())
                .difficulty(form.getDifficulty())
                .questionCount(questionCount)
                .durationMinutes(duration)
                .language(form.getLanguage())
                .persona(form.getPersona())
                .maxFollowUps(maxFollowUps)
                .mode(form.getMode())
                .feedbackTiming(form.getFeedbackTiming())
                .voiceName(voiceName)
                .build();
    }

    private void require(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
