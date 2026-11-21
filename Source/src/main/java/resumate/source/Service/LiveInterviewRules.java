package resumate.source.Service;

import resumate.source.DTO.InterviewProgress;
import resumate.source.Model.InterviewConfig;
import resumate.source.Model.InterviewSession;
import resumate.source.Model.InterviewStatus;
import resumate.source.Model.PlannedQuestion;
import resumate.source.Model.TurnRole;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Các quy tắc dùng chung của buổi phỏng vấn: thời gian, tiến độ, giới hạn lượt, trạng thái.
 * Nơi gọi phải giữ khóa (synchronized) trên session khi đọc/ghi transcript.
 */
public final class LiveInterviewRules {

    private LiveInterviewRules() {
    }

    /**
     * Số giây còn lại; đồng hồ bắt đầu khi ứng viên vào phòng lần đầu.
     */
    public static long remainingSeconds(InterviewSession session, Instant now) {
        long total = session.getConfig().getDurationMinutes() * 60L;

        if (session.getStartedAt() == null) {
            return total;
        }

        long elapsed = Duration.between(session.getStartedAt(), now).getSeconds();
        return Math.max(0, total - elapsed);
    }

    /**
     * Số lượt trả lời tối đa của ứng viên ở chế độ nhắn tin.
     */
    public static int candidateTurnCap(InterviewConfig config) {
        return config.getQuestionCount() * (config.getMaxFollowUps() + 2) + 4;
    }

    public static long candidateTurns(InterviewSession session) {
        return session.getTranscript().stream()
                .filter(turn -> turn.getRole() == TurnRole.CANDIDATE)
                .count();
    }

    public static boolean isEnded(InterviewSession session) {
        return session.getStatus() == InterviewStatus.COMPLETED
                || session.getStatus() == InterviewStatus.REPORT_READY
                || session.getStatus() == InterviewStatus.FAILED;
    }

    /**
     * Đánh dấu buổi phỏng vấn đã kết thúc (gọi nhiều lần vẫn an toàn).
     */
    public static void complete(InterviewSession session, Instant now) {
        if (!isEnded(session)) {
            session.setStatus(InterviewStatus.COMPLETED);
        }

        if (session.getEndedAt() == null) {
            session.setEndedAt(now);
        }
    }

    public static boolean isPlannedQuestion(InterviewSession session, String questionId) {
        return questionId != null && session.getPlan().getQuestions().stream()
                .anyMatch(question -> question.getId().equals(questionId));
    }

    public static InterviewProgress progress(InterviewSession session) {
        List<PlannedQuestion> questions = session.getPlan().getQuestions();
        int current = 0;

        for (int index = 0; index < questions.size(); index++) {
            if (questions.get(index).getId().equals(session.getCurrentQuestionId())) {
                current = index + 1;
                break;
            }
        }

        return InterviewProgress.builder()
                .current(current)
                .total(questions.size())
                .currentQuestionId(session.getCurrentQuestionId())
                .build();
    }
}
