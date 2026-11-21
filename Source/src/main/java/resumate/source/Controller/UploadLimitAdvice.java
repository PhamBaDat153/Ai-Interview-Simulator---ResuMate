package resumate.source.Controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * File tải lên quá lớn ở trang phỏng vấn trực tiếp → quay lại trang thiết lập kèm thông báo.
 * Các trang khác giữ nguyên cách xử lý mặc định của Spring.
 */
@ControllerAdvice
public class UploadLimitAdvice {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSize(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request) throws MaxUploadSizeExceededException {

        String livePrefix = request.getContextPath() + "/interview/live";

        if (request.getRequestURI().startsWith(livePrefix)) {
            return "redirect:/interview/live/setup?uploadError";
        }

        throw exception;
    }
}
