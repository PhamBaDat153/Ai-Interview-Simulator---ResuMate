package resumate.source.Model;

/**
 * Phong cách của người phỏng vấn AI.
 */
public enum InterviewPersona {
    FRIENDLY("Thân thiện"),
    NEUTRAL("Trung lập"),
    STRICT("Nghiêm khắc");

    // Nhãn tiếng Việt hiển thị trên giao diện.
    private final String label;

    InterviewPersona(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
