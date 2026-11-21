package resumate.source.Model;

/**
 * Không tìm thấy buổi phỏng vấn, hoặc buổi phỏng vấn không thuộc về phiên hiện tại.
 */
public class InterviewNotFoundException extends RuntimeException {

    public InterviewNotFoundException() {
        super("Không tìm thấy buổi phỏng vấn");
    }
}
