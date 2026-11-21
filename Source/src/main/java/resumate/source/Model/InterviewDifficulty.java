package resumate.source.Model;

/**
 * Độ khó của buổi phỏng vấn / câu hỏi.
 */
public enum InterviewDifficulty {
    EASY("Dễ"),
    MEDIUM("Trung bình"),
    HARD("Khó");

    // Nhãn tiếng Việt hiển thị trên giao diện.
    private final String label;

    InterviewDifficulty(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
