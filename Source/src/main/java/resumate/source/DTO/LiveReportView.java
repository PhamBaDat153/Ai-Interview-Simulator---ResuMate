package resumate.source.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import resumate.source.Model.InterviewMetrics;
import resumate.source.Model.InterviewReport;

/**
 * Dữ liệu hiển thị trên trang báo cáo.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class LiveReportView {
    // null khi chưa đủ dữ liệu hoặc tạo báo cáo bị lỗi.
    private InterviewReport report;
    private InterviewMetrics metrics;

    // true khi ứng viên trả lời quá ít để đánh giá (không gọi AI).
    private boolean insufficientData;
}
