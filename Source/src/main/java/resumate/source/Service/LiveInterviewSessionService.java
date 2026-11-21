package resumate.source.Service;

import org.springframework.stereotype.Service;
import resumate.source.DTO.LiveRoomState;
import resumate.source.DTO.TranscriptSyncRequest;
import resumate.source.Model.InterviewMode;
import resumate.source.Model.InterviewNotFoundException;
import resumate.source.Model.InterviewSession;
import resumate.source.Model.InterviewStatus;
import resumate.source.Model.TranscriptTurn;
import resumate.source.Repository.InterviewSessionRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Vòng đời của buổi phỏng vấn: kiểm tra quyền truy cập, bắt đầu, đồng bộ transcript, kết thúc.
 */
@Service
public class LiveInterviewSessionService {

    static final int MAX_TRANSCRIPT_TURNS = 600;
    static final int MAX_TURN_CHARS = 5_000;

    private final InterviewSessionRepository repository;
    private final LiveInterviewTextService textService;

    public LiveInterviewSessionService(InterviewSessionRepository repository,
                                       LiveInterviewTextService textService) {
        this.repository = repository;
        this.textService = textService;
    }

    /**
     * Lấy buổi phỏng vấn nếu nó thuộc về HttpSession hiện tại, nếu không coi như không tồn tại.
     */
    public InterviewSession getOwned(String id, String ownerSessionId) {
        return repository.findOwned(id, ownerSessionId)
                .orElseThrow(InterviewNotFoundException::new);
    }

    /**
     * Ứng viên vào phòng: bắt đầu đồng hồ (chỉ lần đầu) và, ở chế độ nhắn tin,
     * để người phỏng vấn chào và hỏi câu đầu tiên. Gọi lại khi tải lại trang vẫn an toàn.
     */
    public LiveRoomState start(InterviewSession session) {
        synchronized (session) {
            if (!LiveInterviewRules.isEnded(session) && session.getStartedAt() == null) {
                session.setStartedAt(Instant.now());
                session.setStatus(InterviewStatus.IN_PROGRESS);
            }
        }

        if (session.getConfig().getMode() == InterviewMode.TEXT) {
            textService.greet(session);
        }

        return state(session);
    }

    public LiveRoomState state(InterviewSession session) {
        synchronized (session) {
            Instant now = Instant.now();

            return LiveRoomState.builder()
                    .mode(session.getConfig().getMode())
                    .turns(List.copyOf(session.getTranscript()))
                    .progress(LiveInterviewRules.progress(session))
                    .remainingSeconds(LiveInterviewRules.remainingSeconds(session, now))
                    .ended(LiveInterviewRules.isEnded(session)
                            || LiveInterviewRules.remainingSeconds(session, now) <= 0)
                    .build();
        }
    }

    public void finish(InterviewSession session) {
        synchronized (session) {
            LiveInterviewRules.complete(session, Instant.now());
        }
    }

    /**
     * Thay transcript đã lưu bằng bản trình duyệt gửi lên (chỉ dùng cho chế độ giọng nói).
     */
    public void syncTranscript(InterviewSession session, TranscriptSyncRequest request) {

        if (request == null || request.getTurns() == null) {
            throw new IllegalArgumentException("Transcript không được để trống");
        }

        if (request.getTurns().size() > MAX_TRANSCRIPT_TURNS) {
            throw new IllegalArgumentException("Transcript quá dài");
        }

        Instant now = Instant.now();
        List<TranscriptTurn> turns = new ArrayList<>();

        for (TranscriptTurn turn : request.getTurns()) {
            if (turn == null || turn.getRole() == null
                    || turn.getText() == null || turn.getText().isBlank()) {
                continue;
            }

            String text = turn.getText().trim();

            turns.add(TranscriptTurn.builder()
                    .role(turn.getRole())
                    .text(text.length() > MAX_TURN_CHARS ? text.substring(0, MAX_TURN_CHARS) : text)
                    .timestamp(turn.getTimestamp() == null ? now : turn.getTimestamp())
                    .build());
        }

        synchronized (session) {
            if (session.getConfig().getMode() != InterviewMode.VOICE) {
                throw new IllegalStateException(
                        "Chỉ buổi phỏng vấn bằng giọng nói mới gửi transcript lên máy chủ");
            }

            if (session.getStatus() == InterviewStatus.REPORT_READY) {
                throw new IllegalStateException("Báo cáo đã được tạo, không thể cập nhật transcript");
            }

            session.setTranscript(turns);

            if (LiveInterviewRules.isPlannedQuestion(session, request.getCurrentQuestionId())) {
                session.setCurrentQuestionId(request.getCurrentQuestionId());
            }
        }
    }

    /**
     * Chuyển sang chế độ nhắn tin (ví dụ khi trình duyệt không hỗ trợ micro).
     */
    public void switchToText(InterviewSession session) {
        synchronized (session) {
            if (LiveInterviewRules.isEnded(session)) {
                throw new IllegalStateException("Buổi phỏng vấn đã kết thúc");
            }

            session.getConfig().setMode(InterviewMode.TEXT);
            session.getConfig().setVoiceName(null);
        }
    }
}
