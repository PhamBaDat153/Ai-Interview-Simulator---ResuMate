package resumate.source.Model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Kế hoạch phỏng vấn do Gemini tạo ra từ CV, JD và cấu hình.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class InterviewPlan {
    private String position;
    private String level;
    private String candidateSummary;
    private List<PlannedQuestion> questions;
}
