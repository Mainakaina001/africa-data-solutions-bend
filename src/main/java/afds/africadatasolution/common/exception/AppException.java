package afds.africadatasolution.common.exception;

import org.springframework.http.HttpStatus;

/** Base class for operational errors — mirrors backend/src/utils/errors.ts#AppError. */
public class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AppException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
