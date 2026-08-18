package afds.africadatasolution.common.exception;

import org.springframework.http.HttpStatus;

public class InsufficientBalanceException extends AppException {
    public InsufficientBalanceException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INSUFFICIENT_BALANCE");
    }
}
