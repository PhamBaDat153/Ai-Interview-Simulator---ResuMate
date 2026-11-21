package resumate.source.Controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import resumate.source.DTO.CreateInterviewSessionRequest;
import resumate.source.DTO.LiveReportView;
import resumate.source.Model.FeedbackTiming;
import resumate.source.Model.InterviewDifficulty;
import resumate.source.Model.InterviewLanguage;
import resumate.source.Model.InterviewLevel;
import resumate.source.Model.InterviewMode;
import resumate.source.Model.InterviewNotFoundException;
import resumate.source.Model.InterviewPersona;
import resumate.source.Model.InterviewSession;
import resumate.source.Model.InterviewType;
import resumate.source.Model.PlannedQuestion;
import resumate.source.Service.InterviewConfigMapper;
import resumate.source.Service.InterviewPromptBuilder;
import resumate.source.Service.InterviewMetricsCalculator;
import resumate.source.Service.InterviewReportService;
import resumate.source.Service.LiveInterviewRules;
import resumate.source.Service.LiveInterviewSessionService;
import resumate.source.Service.LiveInterviewSetupService;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Các trang của tính năng phỏng vấn trực tiếp: thiết lập, phòng chờ, phòng phỏng vấn, kết quả.
 */
@Controller
@RequestMapping("/interview/live")
public class LiveInterviewPageController {

    // Các giọng đọc dựng sẵn của Gemini Live API (để trống = giọng mặc định).
    static final List<String> VOICE_OPTIONS = List.of(
            "Kore", "Puck", "Charon", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr");

    private final LiveInterviewSetupService setupService;
    // Ngày giờ hiển thị trên báo cáo theo giờ Việt Nam.
    private static final DateTimeFormatter REPORT_DATE_FORMAT = DateTimeFormatter
            .ofPattern("HH:mm 'ngày' dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final LiveInterviewSessionService sessionService;
    private final InterviewReportService reportService;
    private final InterviewMetricsCalculator metricsCalculator;
    private final InterviewPromptBuilder promptBuilder;

    public LiveInterviewPageController(
            LiveInterviewSetupService setupService,
            LiveInterviewSessionService sessionService,
            InterviewReportService reportService,
            InterviewMetricsCalculator metricsCalculator,
            InterviewPromptBuilder promptBuilder) {
        this.setupService = setupService;
        this.sessionService = sessionService;
        this.reportService = reportService;
        this.metricsCalculator = metricsCalculator;
        this.promptBuilder = promptBuilder;
    }

    @GetMapping("/setup")
    public String setupPage(
            @RequestParam(value = "uploadError", required = false) String uploadError,
            @RequestParam(value = "from", required = false) String fromInterviewId,
            HttpSession httpSession,
            Model model) {

        model.addAttribute("form", initialForm(fromInterviewId, httpSession));

        if (uploadError != null) {
            model.addAttribute("error",
                    "File tải lên vượt quá dung lượng cho phép (tối đa 5MB mỗi file)");
        }

        addOptions(model);
        return "liveSetup";
    }

    @PostMapping("/setup")
    public String createSession(
            @ModelAttribute("form") CreateInterviewSessionRequest form,
            BindingResult bindingResult,
            HttpSession httpSession,
            Model model) {

        // Giá trị không chuyển được sang enum/số (ví dụ form bị chỉnh sửa) → báo lỗi chung.
        if (bindingResult.hasErrors()) {
            model.addAttribute("error",
                    "Một số giá trị cấu hình không hợp lệ. Vui lòng kiểm tra lại.");
            addOptions(model);
            return "liveSetup";
        }

        try {
            InterviewSession session = setupService.create(form, httpSession.getId());
            return "redirect:/interview/live/" + session.getId();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            // Hiển thị lại form với dữ liệu người dùng đã nhập.
            model.addAttribute("error", exception.getMessage());
            addOptions(model);
            return "liveSetup";
        }
    }

    @GetMapping("/{id}")
    public String lobby(@PathVariable String id, HttpSession httpSession, Model model) {

        InterviewSession session = sessionService.getOwned(id, httpSession.getId());

        model.addAttribute("interview", session);
        return "liveLobby";
    }

    @GetMapping("/{id}/room")
    public String room(@PathVariable String id, HttpSession httpSession, Model model) {

        InterviewSession session = sessionService.getOwned(id, httpSession.getId());

        // Buổi phỏng vấn đã kết thúc thì chuyển thẳng tới trang kết quả.
        if (LiveInterviewRules.isEnded(session)) {
            return "redirect:/interview/live/" + id + "/report";
        }

        model.addAttribute("interview", session);

        // Dữ liệu cho chế độ giọng nói: chỉ ID câu hỏi (không có nội dung) và các thông báo hệ thống.
        model.addAttribute("questionIds", String.join(",", session.getPlan().getQuestions().stream()
                .map(PlannedQuestion::getId)
                .toList()));
        model.addAttribute("kickoffText", promptBuilder.kickoffNote(session.getConfig().getLanguage()));
        model.addAttribute("oneMinuteText", promptBuilder.oneMinuteNote(session.getConfig().getLanguage()));
        model.addAttribute("resumeText", promptBuilder.resumeNote(session.getConfig().getLanguage()));
        return "liveRoom";
    }

    /**
     * Chuyển buổi phỏng vấn sang chế độ nhắn tin (khi không dùng được micro).
     */
    @PostMapping("/{id}/use-text")
    public String useText(@PathVariable String id, HttpSession httpSession) {

        InterviewSession session = sessionService.getOwned(id, httpSession.getId());

        try {
            sessionService.switchToText(session);
            return "redirect:/interview/live/" + id + "/room";
        } catch (IllegalStateException exception) {
            return "redirect:/interview/live/" + id + "/report";
        }
    }

    /**
     * Trang báo cáo: tạo báo cáo bằng AI ở lần xem đầu tiên, các lần sau dùng bản đã lưu.
     */
    @GetMapping("/{id}/report")
    public String report(@PathVariable String id, HttpSession httpSession, Model model) {

        InterviewSession session = sessionService.getOwned(id, httpSession.getId());

        // Chưa kết thúc thì quay lại phòng phỏng vấn.
        if (!LiveInterviewRules.isEnded(session)) {
            return "redirect:/interview/live/" + id + "/room";
        }

        LiveReportView view;

        try {
            view = reportService.getOrCreateReport(session);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            // Tạo báo cáo lỗi: vẫn hiển thị chỉ số và transcript, kèm nút thử lại.
            model.addAttribute("error", exception.getMessage());
            view = LiveReportView.builder()
                    .metrics(metricsCalculator.calculate(session))
                    .build();
        }

        model.addAttribute("interview", session);
        model.addAttribute("view", view);
        model.addAttribute("state", sessionService.state(session));
        Instant interviewTime = session.getStartedAt() != null
                ? session.getStartedAt()
                : (session.getCreatedAt() != null ? session.getCreatedAt() : Instant.now());
        model.addAttribute("interviewDate", REPORT_DATE_FORMAT.format(interviewTime));
        return "liveReport";
    }

    /**
     * Form mặc định, hoặc form điền sẵn từ buổi phỏng vấn trước của chính người dùng.
     */
    private CreateInterviewSessionRequest initialForm(String fromInterviewId, HttpSession httpSession) {
        if (fromInterviewId == null || fromInterviewId.isBlank()) {
            return CreateInterviewSessionRequest.withDefaults();
        }

        try {
            InterviewSession previous = sessionService.getOwned(fromInterviewId, httpSession.getId());
            return CreateInterviewSessionRequest.fromSession(previous);
        } catch (InterviewNotFoundException exception) {
            // Buổi cũ không tồn tại hoặc đã hết hạn: dùng form mặc định.
            return CreateInterviewSessionRequest.withDefaults();
        }
    }

    @ExceptionHandler(InterviewNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String interviewNotFound() {
        return "liveNotFound";
    }

    private void addOptions(Model model) {
        model.addAttribute("levels", InterviewLevel.values());
        model.addAttribute("interviewTypes", InterviewType.values());
        model.addAttribute("difficulties", InterviewDifficulty.values());
        model.addAttribute("languages", InterviewLanguage.values());
        model.addAttribute("personas", InterviewPersona.values());
        model.addAttribute("modes", InterviewMode.values());
        model.addAttribute("feedbackTimings", FeedbackTiming.values());
        model.addAttribute("durations", InterviewConfigMapper.DURATIONS);
        model.addAttribute("minQuestions", InterviewConfigMapper.MIN_QUESTIONS);
        model.addAttribute("maxQuestions", InterviewConfigMapper.MAX_QUESTIONS);
        model.addAttribute("maxFollowUps", InterviewConfigMapper.MAX_FOLLOW_UPS);
        model.addAttribute("voices", VOICE_OPTIONS);
    }
}
