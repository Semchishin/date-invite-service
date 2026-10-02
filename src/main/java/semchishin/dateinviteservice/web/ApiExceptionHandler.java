package semchishin.dateinviteservice.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import semchishin.dateinviteservice.support.ApiException;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException exception) {
        log.warn("Запрос отклонён ({}): {}", exception.getStatus().value(), exception.getMessage());
        return ResponseEntity.status(exception.getStatus()).body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler({NoResourceFoundException.class, HandlerMethodValidationException.class,
            IllegalArgumentException.class, org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            tools.jackson.core.JacksonException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception exception) {
        log.warn("Некорректный запрос: {}", exception.getMessage());
        return ResponseEntity.badRequest().body(new ApiError(message(exception)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        log.error("Не удалось обработать запрос", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Что-то пошло не так"));
    }

    private static String message(Exception exception) {
        if (exception instanceof IllegalArgumentException) {
            return exception.getMessage();
        }
        if (exception instanceof tools.jackson.core.JacksonException) {
            return "Некорректный запрос";
        }
        return "Некорректный запрос";
    }
}
