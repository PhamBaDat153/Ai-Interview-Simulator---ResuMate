package resumate.source.Service;

import resumate.source.DTO.LiveReportView;
import resumate.source.Model.InterviewSession;

public interface InterviewReportService {
    LiveReportView getOrCreateReport(InterviewSession session);
}
