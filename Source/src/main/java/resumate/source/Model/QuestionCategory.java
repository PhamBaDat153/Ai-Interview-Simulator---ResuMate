package resumate.source.Model;

/**
 * Nhóm câu hỏi trong kế hoạch phỏng vấn.
 */
public enum QuestionCategory {
    TECHNICAL("Chuyên môn"),
    BEHAVIORAL("Hành vi"),
    CV_PROJECT("Dự án trong CV"),
    SITUATIONAL("Tình huống");

    // Nhãn tiếng Việt hiển thị trên giao diện.
    private final String label;

    QuestionCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
