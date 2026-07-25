package dev.cctasks.web;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "dev.cctasks.web")
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.status()).body(ApiErrorResponse.of(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiErrorResponse.of("validation_error", message));
    }

    /**
     * enum の値ミスなどは Jackson の内側で IllegalArgumentException になる。
     * 何が悪かったのか分かるよう、根本原因のメッセージを表に出す。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException && cause.getMessage() != null) {
                return ResponseEntity.badRequest()
                        .body(ApiErrorResponse.of("bad_request", cause.getMessage()));
            }
        }
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("bad_request", "リクエストボディを解釈できません"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of("bad_request", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicate(DuplicateKeyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("conflict", "既に存在します"));
    }

    /**
     * Spring Data JDBC は保存時の例外を DbActionExecutionException で包む。
     * 中身が意味のある例外なら、そちらの扱いに寄せる。
     */
    @ExceptionHandler(DbActionExecutionException.class)
    public ResponseEntity<ApiErrorResponse> handleDbAction(DbActionExecutionException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof ApiException apiException) {
            return handleApiException(apiException);
        }
        if (cause instanceof DuplicateKeyException duplicate) {
            return handleDuplicate(duplicate);
        }
        return handleUnexpected(ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("想定外のエラー", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("internal_error", "サーバー内部エラー"));
    }
}
