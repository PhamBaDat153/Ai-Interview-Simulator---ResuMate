package resumate.source.Model;

/**
 * Ngôn ngữ dùng trong buổi phỏng vấn.
 */
public enum InterviewLanguage {
    VI("Tiếng Việt"),
    EN("Tiếng Anh");

    // Nhãn tiếng Việt hiển thị trên giao diện.
    private final String label;

    InterviewLanguage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
