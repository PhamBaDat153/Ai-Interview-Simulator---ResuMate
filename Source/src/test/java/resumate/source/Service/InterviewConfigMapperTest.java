package resumate.source.Service;

import org.junit.jupiter.api.Test;
import resumate.source.DTO.CreateInterviewSessionRequest;
import resumate.source.Model.InterviewConfig;
import resumate.source.Model.InterviewMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewConfigMapperTest {

    private final InterviewConfigMapper mapper = new InterviewConfigMapper();

    @Test
    void defaultsAreValid() {
        CreateInterviewSessionRequest form = CreateInterviewSessionRequest.withDefaults();
        form.setTargetPosition("  Java Backend Developer  ");
        form.setVoiceName("Kore");

        InterviewConfig config = mapper.toConfig(form);

        assertThat(config.getTargetPosition()).isEqualTo("Java Backend Developer");
        assertThat(config.getQuestionCount()).isEqualTo(5);
        assertThat(config.getDurationMinutes()).isEqualTo(10);
        assertThat(config.getMaxFollowUps()).isEqualTo(1);
        assertThat(config.getVoiceName()).isEqualTo("Kore");
    }

    @Test
    void blankPositionBecomesNull() {
        CreateInterviewSessionRequest form = CreateInterviewSessionRequest.withDefaults();
        form.setTargetPosition("   ");

        assertThat(mapper.toConfig(form).getTargetPosition()).isNull();
    }

    @Test
    void voiceNameIsIgnoredInTextMode() {
        CreateInterviewSessionRequest form = CreateInterviewSessionRequest.withDefaults();
        form.setMode(InterviewMode.TEXT);
        form.setVoiceName("Kore");

        assertThat(mapper.toConfig(form).getVoiceName()).isNull();
    }

    @Test
    void questionCountOutOfRangeIsRejected() {
        CreateInterviewSessionRequest form = CreateInterviewSessionRequest.withDefaults();
        form.setQuestionCount(11);

        assertThatThrownBy(() -> mapper.toConfig(form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Số câu hỏi phải từ 3 đến 10");
    }

    @Test
    void unsupportedDurationIsRejected() {
        CreateInterviewSessionRequest form = CreateInterviewSessionRequest.withDefaults();
        form.setDurationMinutes(7);

        assertThatThrownBy(() -> mapper.toConfig(form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5, 10 hoặc 15 phút");
    }

    @Test
    void invalidVoiceNameIsRejected() {
        CreateInterviewSessionRequest form = CreateInterviewSessionRequest.withDefaults();
        form.setVoiceName("<script>");

        assertThatThrownBy(() -> mapper.toConfig(form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Giọng đọc không hợp lệ");
    }

    @Test
    void missingLevelIsRejected() {
        CreateInterviewSessionRequest form = CreateInterviewSessionRequest.withDefaults();
        form.setLevel(null);

        assertThatThrownBy(() -> mapper.toConfig(form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Vui lòng chọn cấp độ ứng viên");
    }
}
