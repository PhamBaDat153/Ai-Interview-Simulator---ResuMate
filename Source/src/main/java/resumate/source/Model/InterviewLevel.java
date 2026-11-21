package resumate.source.Model;

/**
 * Cấp độ của ứng viên.
 */
public enum InterviewLevel {
    INTERN("Thực tập sinh"),
    FRESHER("Fresher"),
    JUNIOR("Junior"),
    MIDDLE("Middle"),
    SENIOR("Senior");

    // Nhãn tiếng Việt hiển thị trên giao diện.
    private final String label;

    InterviewLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
