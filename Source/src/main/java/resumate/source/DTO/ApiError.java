package resumate.source.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class ApiError {
    // Thông báo lỗi tiếng Việt hiển thị cho người dùng.
    private String error;
}
