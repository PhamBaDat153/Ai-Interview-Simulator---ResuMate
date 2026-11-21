package resumate.source.Service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import resumate.source.Ultilities.TextExtractor;

import java.util.Locale;

/**
 * Lấy nội dung tài liệu (CV, JD) từ văn bản nhập trực tiếp
 * hoặc từ file PDF/DOCX người dùng tải lên.
 */
@Service
public class DocumentInputService {

    /**
     * Kiểm tra người dùng đã cung cấp tài liệu dưới dạng văn bản hoặc file hay chưa.
     */
    public boolean isProvided(String text, MultipartFile file) {
        return hasText(text) || (file != null && !file.isEmpty());
    }

    /**
     * Dừng xử lý nếu người dùng không cung cấp tài liệu dưới bất kỳ hình thức nào.
     */
    public void requireProvided(String text, MultipartFile file, String label) {
        if (!isProvided(text, file)) {
            throw new IllegalArgumentException(label + " không được để trống");
        }
    }

    /**
     * Trả về nội dung tài liệu: ưu tiên văn bản nhập trực tiếp,
     * nếu không có thì trích xuất từ file đã tải lên.
     */
    public String resolve(String text, MultipartFile file, String label) {

        requireProvided(text, file, label);

        String content = hasText(text)
                ? text
                : extractFileContent(file);

        // Kiểm tra kết quả trích xuất để tránh gửi nội dung rỗng đến Gemini API.
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    "Không thể trích xuất nội dung từ " + label);
        }

        return content;
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    /**
     * Trích xuất nội dung văn bản từ file PDF hoặc DOCX
     * dựa trên phần mở rộng của tên file.
     */
    private String extractFileContent(MultipartFile file) {

        // Kiểm tra file có tồn tại và chứa dữ liệu trước khi đọc.
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "File không được để trống");
        }

        // Lấy tên file để xác định định dạng cần xử lý.
        String filename = file.getOriginalFilename();

        // Từ chối file nếu không có tên hợp lệ.
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException(
                    "Tên file không hợp lệ");
        }

        // Chuyển tên file về chữ thường để việc kiểm tra phần mở rộng
        // không phụ thuộc vào cách viết hoa hoặc viết thường.
        String lowerFilename = filename.toLowerCase(Locale.ROOT);

        // Nếu là PDF, sử dụng TextExtractor để đọc nội dung văn bản từ file.
        if (lowerFilename.endsWith(".pdf")) {
            return TextExtractor.extractTextFromPdf(file);
        }

        // Nếu là DOCX, sử dụng phương thức trích xuất dành riêng cho Word.
        if (lowerFilename.endsWith(".docx")) {
            return TextExtractor.extractTextFromDocx(file);
        }

        // Từ chối các định dạng file không được hệ thống hỗ trợ.
        throw new IllegalArgumentException(
                "Chỉ hỗ trợ file PDF và DOCX");
    }
}
