package afds.africadatasolution.modules.notification.service;

import afds.africadatasolution.modules.notification.NotificationPayload;

import java.util.List;
import java.util.UUID;

/**
 * Firebase Cloud Messaging push notifications. Mirrors backend/src/services/notification.service.ts.
 * Push is a best-effort side channel — failures here never propagate to the caller.
 */
public interface NotificationService {

    boolean sendToUser(UUID userId, NotificationPayload notification);

    boolean sendToToken(String token, NotificationPayload notification);

    SendResult sendToMultipleUsers(List<UUID> userIds, NotificationPayload notification);

    void saveToken(UUID userId, String fcmToken);

    void clearToken(UUID userId);

    record SendResult(int success, int failed) {
    }
}
