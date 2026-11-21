package resumate.source.Model;

/**
 * Kết luận tuyển dụng trong báo cáo phỏng vấn.
 */
public enum Verdict {
    STRONG_HIRE("Rất nên tuyển"),
    HIRE("Nên tuyển"),
    LEAN_HIRE("Cân nhắc"),
    NO_HIRE("Chưa phù hợp");

    // Nhãn tiếng Việt hiển thị trên giao diện.
    private final String label;

    Verdict(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Kết luận tương ứng với điểm tổng (dùng khi AI không trả về kết luận hợp lệ).
     */
    public static Verdict fromScore(int overallScore) {
        if (overallScore >= 85) {
            return STRONG_HIRE;
        }

        if (overallScore >= 70) {
            return HIRE;
        }

        if (overallScore >= 55) {
            return LEAN_HIRE;
        }

        return NO_HIRE;
    }
}
