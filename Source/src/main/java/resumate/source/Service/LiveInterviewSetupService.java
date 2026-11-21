package resumate.source.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import resumate.source.DTO.CreateInterviewSessionRequest;
import resumate.source.Model.InterviewConfig;
import resumate.source.Model.InterviewPlan;
import resumate.source.Model.InterviewSession;
import resumate.source.Model.InterviewStatus;
import resumate.source.Repository.InterviewSessionRepository;

import java.time.Instant;
import java.util.UUID;

/**
 * Xử lý form thiết lập: kiểm tra cấu hình, lấy nội dung CV/JD,
 * tạo kế hoạch phỏng vấn và lưu buổi phỏng vấn mới.
 */
@Service
public class LiveInterviewSetupService {

    private static final Logger log = LoggerFactory.getLogger(LiveInterviewSetupService.class);

    public static final int MAX_DOCUMENT_CHARS = 20_000;
    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    private final DocumentInputService documentInputService;
    private final InterviewConfigMapper configMapper;
    private final InterviewPlanService planService;
    private final InterviewSessionRepository repository;

    public LiveInterviewSetupService(
            DocumentInputService documentInputService,
            InterviewConfigMapper configMapper,
            InterviewPlanService planService,
            InterviewSessionRepository repository) {

        this.documentInputService = documentInputService;
        this.configMapper = configMapper;
        this.planService = planService;
        this.repository = repository;
    }

    /**
     * Tạo buổi phỏng vấn mới thuộc về HttpSession ownerSessionId.
     * Nội dung CV/JD đã trích xuất được ghi lại vào form để nếu bước tạo kế hoạch lỗi,
     * người dùng không phải tải lại file.
     */
    public InterviewSession create(CreateInterviewSessionRequest form, String ownerSessionId) {

        // Kiểm tra cấu hình trước vì bước này nhanh và không tốn chi phí.
        InterviewConfig config = configMapper.toConfig(form);

        String cv = resolveDocument(form.getCvText(), form.getCvFile(), "CV");
        form.setCvText(cv);

        String jd = resolveDocument(form.getJdText(), form.getJdFile(), "Mô tả công việc");
        form.setJdText(jd);

        InterviewPlan plan = planService.generate(cv, jd, config);

        InterviewSession session = InterviewSession.builder()
                .id(UUID.randomUUID().toString())
                .ownerSessionId(ownerSessionId)
                .cvText(cv)
                .jdText(jd)
                .config(config)
                .plan(plan)
                .status(InterviewStatus.CREATED)
                .createdAt(Instant.now())
                .build();

        repository.save(session);

        log.info("Đã tạo buổi phỏng vấn {} ({} câu hỏi, chế độ {})",
                session.getId(), plan.getQuestions().size(), config.getMode());

        return session;
    }

    /**
     * Lấy nội dung tài liệu và kiểm tra giới hạn dung lượng file / số ký tự.
     */
    private String resolveDocument(String text, MultipartFile file, String label) {

        boolean usesFile = text == null || text.isBlank();

        if (usesFile && file != null && file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException(
                    "File " + label + " vượt quá dung lượng tối đa 5MB");
        }

        String content = documentInputService.resolve(text, file, label).trim();

        if (content.length() > MAX_DOCUMENT_CHARS) {
            throw new IllegalArgumentException(
                    label + " quá dài (" + formatNumber(content.length())
                            + " ký tự). Vui lòng rút gọn còn tối đa "
                            + formatNumber(MAX_DOCUMENT_CHARS) + " ký tự.");
        }

        return content;
    }

    private String formatNumber(int value) {
        return "%,d".formatted(value).replace(',', '.');
    }
}
