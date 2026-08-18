package afds.africadatasolution.common.exception;

/**
 * Classifies an upstream delivery failure so callers know whether it is safe
 * to refund immediately or must be left for the reconciliation worker.
 *
 * Mirrors the {@code classifyDeliveryError} logic in backend/src/services/smeplug.service.ts.
 */
public enum FailureClassification {
    /** Provider explicitly rejected the request — safe to refund now. */
    DEFINITIVE_FAILURE,
    /** Timeout / network error / unknown state — provider may have processed it; leave PROCESSING. */
    AMBIGUOUS
}
