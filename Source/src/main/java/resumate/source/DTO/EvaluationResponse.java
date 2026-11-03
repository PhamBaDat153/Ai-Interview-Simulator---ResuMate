package resumate.source.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class EvaluationResponse {
    private int evaluationScore;
    private String summary;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> improvements;
}
