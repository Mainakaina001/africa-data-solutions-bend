package afds.africadatasolution.common.exception;

import org.springframework.http.HttpStatus;

/** Raised when a downstream provider (Billstack / SME Plug / VTPass / Gmail SMTP) fails. */
public class ExternalServiceException extends AppException {

    private final FailureClassification classification;

    public ExternalServiceException(String service, String message) {
        this(service, message, FailureClassification.AMBIGUOUS);
    }

    public ExternalServiceException(String service, String message, FailureClassification classification) {
        super(service + ": " + message, HttpStatus.BAD_GATEWAY, "EXTERNAL_SERVICE_ERROR");
        this.classification = classification;
    }

    public FailureClassification getClassification() {
        return classification;
    }
}
