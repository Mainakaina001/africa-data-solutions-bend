package afds.africadatasolution.modules.audit.service;

import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit logging. Never throws — a failure to write the audit row
 * must not break the user-facing request, but is logged loudly.
 * Mirrors backend/src/services/audit.service.ts.
 */
public interface AuditService {

    void log(UUID userId, String action, String ip, String userAgent, Map<String, Object> metadata, boolean success);

    void log(UUID userId, String action, String ip, String userAgent);
}
