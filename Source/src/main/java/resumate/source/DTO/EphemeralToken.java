package resumate.source.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class EphemeralToken {
    // Không bao giờ in token ra log.
    @ToString.Exclude
    private String token;
    private Instant expiresAt;
}
