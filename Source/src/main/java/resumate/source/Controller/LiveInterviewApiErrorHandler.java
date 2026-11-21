package resumate.source.Controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import resumate.source.DTO.ApiError;
import resumate.source.Model.InterviewNotFoundException;

/**
 * Chuyển lỗi của API phòng phỏng vấn thành JSON { "error": "..." } bằng tiếng Việt.
 */
@RestControllerAdvice(assignableTypes = LiveInterviewApiController.class)
public class LiveInterviewApiErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(LiveInterviewApiErrorHandler.class);

    @ExceptionHandler(InterviewNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError notFound(InterviewNotFoundException exception) {
        return new ApiError(exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError badRequest(IllegalArgumentException exception) {
        return new ApiError(exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError unreadable(HttpMessageNotReadableException exception) {
        return new ApiError("Dữ liệu gửi lên không hợp lệ");
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError conflict(IllegalStateException exception) {
        return new ApiError(exception.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError unexpected(RuntimeException exception) {
        log.error("Lỗi không mong muốn ở API phỏng vấn trực tiếp", exception);
        return new ApiError("Đã có lỗi xảy ra. Vui lòng thử lại.");
    }
}
