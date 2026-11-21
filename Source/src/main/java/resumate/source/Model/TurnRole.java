package resumate.source.Model;

/**
 * Người nói trong một lượt hội thoại.
 */
public enum TurnRole {
    INTERVIEWER("Người phỏng vấn"),
    CANDIDATE("Ứng viên");

    // Nhãn tiếng Việt hiển thị trên giao diện.
    private final String label;

    TurnRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
