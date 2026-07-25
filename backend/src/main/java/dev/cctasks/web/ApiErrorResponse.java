package dev.cctasks.web;

/**
 * 仕様書 §7.1 のエラー表現: {@code { "error": { "code": "...", "message": "..." } }}
 */
public record ApiErrorResponse(Body error) {

    public record Body(String code, String message) {
    }

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(new Body(code, message));
    }
}
