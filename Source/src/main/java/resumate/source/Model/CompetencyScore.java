package resumate.source.Model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Điểm một năng lực (0-10) trong báo cáo.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class CompetencyScore {
    private String name;
    private int score;
    private String comment;
}
