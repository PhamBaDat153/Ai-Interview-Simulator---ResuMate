package resumate.source.Model;

/**
 * Loại buổi phỏng vấn.
 */
public enum InterviewType {
    TECHNICAL("Kỹ thuật"),
    BEHAVIORAL("Hành vi"),
    MIXED("Kết hợp"),
    HR_SCREENING("Sàng lọc nhân sự (HR)");

    // Nhãn tiếng Việt hiển thị trên giao diện.
    private final String label;

    InterviewType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
