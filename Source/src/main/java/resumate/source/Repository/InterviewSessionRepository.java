package resumate.source.Repository;

import org.springframework.stereotype.Repository;
import resumate.source.Model.InterviewSession;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lưu các buổi phỏng vấn trong bộ nhớ (chưa dùng cơ sở dữ liệu).
 */
@Repository
public class InterviewSessionRepository {

    private final Map<String, InterviewSession> sessions = new ConcurrentHashMap<>();

    public InterviewSession save(InterviewSession session) {
        sessions.put(session.getId(), session);
        return session;
    }

    public Optional<InterviewSession> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(sessions.get(id));
    }

    /**
     * Chỉ trả về buổi phỏng vấn nếu nó thuộc về HttpSession đang yêu cầu.
     */
    public Optional<InterviewSession> findOwned(String id, String ownerSessionId) {
        return findById(id)
                .filter(session -> session.getOwnerSessionId() != null
                        && session.getOwnerSessionId().equals(ownerSessionId));
    }

    /**
     * Xóa các buổi phỏng vấn được tạo trước thời điểm cutoff, trả về số lượng đã xóa.
     */
    public int deleteCreatedBefore(Instant cutoff) {
        int before = sessions.size();
        sessions.values().removeIf(session -> session.getCreatedAt() != null
                && session.getCreatedAt().isBefore(cutoff));
        return before - sessions.size();
    }

    public int count() {
        return sessions.size();
    }
}
