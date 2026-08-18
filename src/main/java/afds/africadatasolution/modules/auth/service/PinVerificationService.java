package afds.africadatasolution.modules.auth.service;

import java.util.UUID;

/**
 * Transaction-PIN gate required on every value-moving endpoint (data/airtime/bills purchases).
 * Mirrors backend/src/middlewares/pinVerify.ts.
 *
 * Tracks failed attempts and locks the PIN — not the whole account — for 15
 * minutes after too many consecutive failures. Login still works; the user
 * must reset their PIN via the change-pin flow.
 */
public interface PinVerificationService {

    void verify(UUID userId, String pin);
}
