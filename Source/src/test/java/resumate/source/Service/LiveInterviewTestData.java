package resumate.source.Service;

import resumate.source.DTO.CreateInterviewSessionRequest;
import resumate.source.Model.InterviewConfig;
import resumate.source.Model.InterviewDifficulty;
import resumate.source.Model.InterviewMode;
import resumate.source.Model.InterviewPlan;
import resumate.source.Model.InterviewSession;
import resumate.source.Model.InterviewStatus;
import resumate.source.Model.PlannedQuestion;
import resumate.source.Model.QuestionCategory;

import java.time.Instant;
import java.util.List;

/**
 * Dữ liệu mẫu dùng chung cho các bài test phỏng vấn trực tiếp.
 */
final class LiveInterviewTestData {

    private LiveInterviewTestData() {
    }

    static InterviewSession textSession() {
        CreateInterviewSessionRequest form = CreateInterviewSessionRequest.withDefaults();
        form.setMode(InterviewMode.TEXT);
        form.setQuestionCount(3);
        InterviewConfig config = new InterviewConfigMapper().toConfig(form);

        List<PlannedQuestion> questions = List.of(
                question("q1", "Hãy kể về dự án Spring Boot gần nhất?"),
                question("q2", "Bạn tối ưu truy vấn SQL như thế nào?"),
                question("q3", "Bạn xử lý bất đồng trong nhóm ra sao?"));

        return InterviewSession.builder()
                .id("session-1")
                .ownerSessionId("owner")
                .cvText("CV: 2 năm Java, dự án ShopX dùng Spring Boot")
                .jdText("JD: Backend Java, yêu cầu Spring Boot, SQL")
                .config(config)
                .plan(InterviewPlan.builder()
                        .position("Backend Developer")
                        .level("JUNIOR")
                        .candidateSummary("Ứng viên có 2 năm kinh nghiệm Java.")
                        .questions(questions)
                        .build())
                .status(InterviewStatus.CREATED)
                .createdAt(Instant.now())
                .build();
    }

    static PlannedQuestion question(String id, String text) {
        return PlannedQuestion.builder()
                .id(id)
                .category(QuestionCategory.TECHNICAL)
                .question(text)
                .purpose("Kiểm tra kinh nghiệm")
                .expectedPoints(List.of("ý A", "ý B"))
                .difficulty(InterviewDifficulty.MEDIUM)
                .build();
    }
}
