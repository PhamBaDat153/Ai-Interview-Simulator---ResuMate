package resumate.source.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.ui.Model;
import resumate.source.DTO.EvaluationRequest;
import resumate.source.Service.EvaluateService;

@Controller
public class CvEvaluationPageController {
    private final EvaluateService evaluateService;

    public CvEvaluationPageController(EvaluateService evaluateService) {
        this.evaluateService = evaluateService;
    }

    @GetMapping("/cv-evaluation")
    public String cvEvaluationPage() {
        return "cvEvaluation";
    }

    @PostMapping("/cv-evaluation")
    public String evaluate(EvaluationRequest request, Model model) {
        try {
            model.addAttribute("result", evaluateService.evaluate(request));
            return "evaluationResult";
        } catch (IllegalArgumentException | IllegalStateException exception) {
            model.addAttribute("error", exception.getMessage());
            return "cvEvaluation";
        }
    }

}
