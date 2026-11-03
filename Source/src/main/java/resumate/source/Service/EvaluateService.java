package resumate.source.Service;

import resumate.source.DTO.EvaluationRequest;
import resumate.source.DTO.EvaluationResponse;

public interface EvaluateService {
    public EvaluationResponse evaluate(EvaluationRequest evaluationRequest);
}
