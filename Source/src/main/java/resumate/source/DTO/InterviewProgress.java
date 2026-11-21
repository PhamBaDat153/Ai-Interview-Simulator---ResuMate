package resumate.source.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tiến độ phỏng vấn hiển thị trên giao diện ("Câu 2/5").
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class InterviewProgress {
    // Thứ tự câu hỏi hiện tại (bắt đầu từ 1); 0 khi chưa hỏi câu nào.
    private int current;
    private int total;
    private String currentQuestionId;
}
