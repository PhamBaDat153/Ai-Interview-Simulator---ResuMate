package resumate.source.Model;

/**
 * Thời điểm người phỏng vấn đưa ra nhận xét.
 */
public enum FeedbackTiming {
    END_ONLY("Chỉ nhận xét khi kết thúc"),
    AFTER_EACH("Nhận xét ngắn sau mỗi câu");

    // Nhãn tiếng Việt hiển thị trên giao diện.
    private final String label;

    FeedbackTiming(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
