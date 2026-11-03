package resumate.source.Ultilities;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

public class TextExtractor {

    /**
     * Hàm đọc và trích xuất nội dung văn bản từ file PDF
     * sử dụng thư viện Apache PDFBox.
     */
    public static String extractTextFromPdf(MultipartFile file) {

        // Gọi hàm validateFile() để kiểm tra file PDF có tồn tại
        // và có chứa dữ liệu trước khi tiến hành đọc.
        validateFile(file, "PDF");

        try {
            // Chuyển nội dung file PDF thành mảng byte
            // để thư viện PDFBox có thể xử lý.
            byte[] fileBytes = file.getBytes();

            // Sử dụng Loader.loadPDF() để đọc dữ liệu
            // và tạo đối tượng PDDocument đại diện cho tài liệu PDF.
            try (PDDocument document = Loader.loadPDF(fileBytes)) {

                // Khởi tạo PDFTextStripper để thực hiện trích xuất văn bản từ PDF.
                PDFTextStripper textStripper = new PDFTextStripper();

                // Lấy toàn bộ nội dung văn bản có trong tài liệu PDF.
                String text = textStripper.getText(document);

                // Gọi cleanText() để làm sạch nội dung vừa trích xuất
                // rồi trả về kết quả.
                return cleanText(text);
            }
        } catch (IOException exception) {

            // Nếu xảy ra lỗi trong quá trình đọc file PDF,
            // thông báo lỗi và giữ lại nguyên nhân gây ra lỗi.
            throw new IllegalArgumentException(
                    "Không thể đọc nội dung file PDF",
                    exception
            );
        }
    }


    /**
     * Hàm đọc và trích xuất nội dung văn bản từ file Word DOCX
     * sử dụng thư viện Apache POI.
     */
    public static String extractTextFromDocx(MultipartFile file) {

        // Kiểm tra file DOCX có tồn tại và không bị rỗng.
        validateFile(file, "DOCX");

        // Mở luồng đọc dữ liệu từ file upload.
        // XWPFDocument sử dụng InputStream để đọc cấu trúc tài liệu Word.
        // Các tài nguyên sẽ tự động được đóng sau khi hoàn thành xử lý.
        try (InputStream inputStream = file.getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream)) {

            // Tạo StringBuilder để lưu và nối nội dung các đoạn văn bản.
            StringBuilder text = new StringBuilder();

            // Duyệt qua từng đoạn văn (paragraph) trong tài liệu Word.
            for (XWPFParagraph paragraph : document.getParagraphs()) {

                // Lấy nội dung văn bản của đoạn hiện tại và nối vào StringBuilder.
                text.append(paragraph.getText());

                // Thêm ký tự xuống dòng để phân tách các đoạn văn bản.
                text.append(System.lineSeparator());
            }

            // Chuyển nội dung StringBuilder thành String,
            // sau đó gọi cleanText() để chuẩn hóa văn bản trước khi trả về.
            return cleanText(text.toString());

        } catch (IOException exception) {

            // Nếu không thể đọc file DOCX thì thông báo lỗi
            // và giữ lại nguyên nhân gây ra lỗi.
            throw new IllegalArgumentException(
                    "Không thể đọc nội dung file DOCX",
                    exception
            );
        }
    }


    /**
     * Hàm kiểm tra tính hợp lệ của file được người dùng upload,
     * đảm bảo file tồn tại và không bị rỗng.
     */
    private static void validateFile(MultipartFile file, String fileType) {

        // Kiểm tra file có null hoặc không chứa dữ liệu hay không.
        if (file == null || file.isEmpty()) {

            // Nếu file không hợp lệ thì ném ra ngoại lệ
            // kèm thông báo tương ứng với định dạng file.
            throw new IllegalArgumentException(
                    "File " + fileType + " không được để trống"
            );
        }
    }


    /**
     * Hàm làm sạch và chuẩn hóa nội dung văn bản sau khi
     * được trích xuất từ file PDF hoặc DOCX.
     */
    private static String cleanText(String text) {

        // Kiểm tra chuỗi đầu vào có null hay không.
        // Nếu null thì trả về chuỗi rỗng để tránh lỗi khi xử lý.
        if (text == null) {
            return "";
        }

        // Thực hiện loại bỏ các ký tự không cần thiết
        // và chuẩn hóa khoảng trắng trong văn bản.
        return text

                // Xóa các ký tự null có thể xuất hiện trong nội dung file.
                .replace("\u0000", "")

                // Gộp nhiều dấu cách hoặc tab liên tiếp thành một dấu cách.
                .replaceAll("[ \\t]+", " ")

                // Rút gọn từ 3 dòng trống liên tiếp trở lên thành 2 dòng trống.
                .replaceAll("\\n{3,}", "\n\n")

                // Xóa khoảng trắng và ký tự xuống dòng ở đầu và cuối chuỗi.
                .trim();
    }
}
