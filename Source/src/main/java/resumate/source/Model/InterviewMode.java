package resumate.source.Model;

/**
 * Hình thức phỏng vấn.
 */
public enum InterviewMode {
    VOICE("Giọng nói"),
    TEXT("Nhắn tin");

    // Nhãn tiếng Việt hiển thị trên giao diện.
    private final String label;

    InterviewMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
