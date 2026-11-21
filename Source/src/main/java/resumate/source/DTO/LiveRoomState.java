package resumate.source.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import resumate.source.Model.InterviewMode;
import resumate.source.Model.TranscriptTurn;

import java.util.List;

/**
 * Trạng thái phòng phỏng vấn trả về khi vào (hoặc tải lại) phòng.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class LiveRoomState {
    private InterviewMode mode;

    @ToString.Exclude
    private List<TranscriptTurn> turns;
    private InterviewProgress progress;
    private long remainingSeconds;
    private boolean ended;
}
