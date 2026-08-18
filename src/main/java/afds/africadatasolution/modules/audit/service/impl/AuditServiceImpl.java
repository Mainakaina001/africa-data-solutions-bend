package afds.africadatasolution.modules.audit.service.impl;

import afds.africadatasolution.domain.audit.AuditLog;
import afds.africadatasolution.domain.audit.AuditLogRepository;
import afds.africadatasolution.modules.audit.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final AuditLogRepository auditLogRepository;

    public AuditServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /** Runs in its own transaction so a rollback of the caller's transaction doesn't erase the audit trail. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void log(UUID userId, String action, String ip, String userAgent, Map<String, Object> metadata, boolean success) {
        try {
            AuditLog entry = new AuditLog();
            entry.setUserId(userId);
            entry.setAction(action);
            entry.setIp(ip);
            entry.setUserAgent(userAgent);
            if (metadata != null) entry.setMetadata(metadata);
            entry.setSuccess(success);
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to write audit log action={} error={}", action, e.getMessage());
        }
    }

    @Override
    public void log(UUID userId, String action, String ip, String userAgent) {
        log(userId, action, ip, userAgent, null, true);
    }
}
