package resumate.source.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class EvaluationRequest {
    private String cvText;
    private MultipartFile cvFile;
    private String jdText;
    private MultipartFile jdFile;
}
