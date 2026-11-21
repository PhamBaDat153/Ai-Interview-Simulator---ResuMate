package resumate.source.Model;

/**
 * Trạng thái của một buổi phỏng vấn.
 */
public enum InterviewStatus {
    CREATED("Đã tạo"),
    IN_PROGRESS("Đang phỏng vấn"),
    COMPLETED("Đã kết thúc"),
    REPORT_READY("Đã có báo cáo"),
    FAILED("Lỗi");

    // Nhãn tiếng Việt hiển thị trên giao diện.
    private final String label;

    InterviewStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
