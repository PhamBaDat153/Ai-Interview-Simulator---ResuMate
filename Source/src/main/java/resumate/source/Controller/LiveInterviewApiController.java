package resumate.source.Controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import resumate.source.DTO.LiveMessageRequest;
import resumate.source.DTO.LiveMessageResponse;
import resumate.source.DTO.LiveRoomState;
import resumate.source.DTO.LiveTokenResponse;
import resumate.source.DTO.TranscriptSyncRequest;
import resumate.source.Model.InterviewSession;
import resumate.source.Service.LiveInterviewSessionService;
import resumate.source.Service.LiveInterviewTextService;
import resumate.source.Service.LiveTokenService;

/**
 * API JSON cho phòng phỏng vấn. Mọi endpoint chỉ dành cho HttpSession đã tạo buổi phỏng vấn.
 */
@RestController
@RequestMapping("/api/interview/live/{id}")
public class LiveInterviewApiController {

    private final LiveInterviewSessionService sessionService;
    private final LiveInterviewTextService textService;
    private final LiveTokenService tokenService;

    public LiveInterviewApiController(LiveInterviewSessionService sessionService,
                                      LiveInterviewTextService textService,
                                      LiveTokenService tokenService) {
        this.sessionService = sessionService;
        this.textService = textService;
        this.tokenService = tokenService;
    }

    /**
     * Chế độ giọng nói: cấp ephemeral token (khóa API thật không bao giờ gửi tới trình duyệt).
     */
    @PostMapping("/token")
    public ResponseEntity<LiveTokenResponse> token(@PathVariable String id, HttpSession httpSession) {
        InterviewSession session = sessionService.getOwned(id, httpSession.getId());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(tokenService.mint(session));
    }

    /**
     * Vào phòng (hoặc tải lại trang): trả về transcript, tiến độ và thời gian còn lại.
     */
    @PostMapping("/start")
    public LiveRoomState start(@PathVariable String id, HttpSession httpSession) {
        InterviewSession session = sessionService.getOwned(id, httpSession.getId());
        return sessionService.start(session);
    }

    /**
     * Chế độ nhắn tin: ứng viên gửi câu trả lời, nhận lời người phỏng vấn.
     */
    @PostMapping("/messages")
    public LiveMessageResponse sendMessage(@PathVariable String id,
                                           @RequestBody LiveMessageRequest request,
                                           HttpSession httpSession) {
        InterviewSession session = sessionService.getOwned(id, httpSession.getId());
        return textService.reply(session, request == null ? null : request.getText());
    }

    /**
     * Chế độ giọng nói: trình duyệt gửi transcript định kỳ và khi kết thúc.
     */
    @PostMapping("/transcript")
    public ResponseEntity<Void> syncTranscript(@PathVariable String id,
                                               @RequestBody TranscriptSyncRequest request,
                                               HttpSession httpSession) {
        InterviewSession session = sessionService.getOwned(id, httpSession.getId());
        sessionService.syncTranscript(session, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/finish")
    public ResponseEntity<Void> finish(@PathVariable String id, HttpSession httpSession) {
        InterviewSession session = sessionService.getOwned(id, httpSession.getId());
        sessionService.finish(session);
        return ResponseEntity.noContent().build();
    }
}
