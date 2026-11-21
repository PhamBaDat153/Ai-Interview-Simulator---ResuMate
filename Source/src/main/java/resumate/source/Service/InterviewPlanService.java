package resumate.source.Service;

import resumate.source.Model.InterviewConfig;
import resumate.source.Model.InterviewPlan;

public interface InterviewPlanService {
    InterviewPlan generate(String cvText, String jdText, InterviewConfig config);
}
