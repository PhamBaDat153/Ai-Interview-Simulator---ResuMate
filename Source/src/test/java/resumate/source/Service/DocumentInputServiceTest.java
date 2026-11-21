package resumate.source.Service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentInputServiceTest {

    private final DocumentInputService service = new DocumentInputService();

    @Test
    void textTakesPriorityOverFile() {
        MockMultipartFile file = new MockMultipartFile(
                "cvFile", "cv.pdf", "application/pdf", "not a real pdf".getBytes());

        assertThat(service.resolve("Nội dung CV", file, "CV")).isEqualTo("Nội dung CV");
    }

    @Test
    void missingTextAndFileThrows() {
        assertThatThrownBy(() -> service.resolve("  ", null, "CV"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CV không được để trống");
    }

    @Test
    void unsupportedFileTypeThrows() {
        MockMultipartFile file = new MockMultipartFile(
                "cvFile", "cv.txt", "text/plain", "abc".getBytes());

        assertThatThrownBy(() -> service.resolve(null, file, "CV"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Chỉ hỗ trợ file PDF và DOCX");
    }
}
