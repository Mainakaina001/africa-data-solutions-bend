package afds.africadatasolution.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** Standardized API envelope — mirrors backend/src/utils/response.ts. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        ErrorDetail error,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> success(String message) {
        return success(message, null);
    }

    public static <T> ApiResponse<T> error(String message, String code, Object details) {
        return new ApiResponse<>(false, message, null, new ErrorDetail(code, details), Instant.now());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorDetail(String code, Object details) {
    }
}
