package afds.africadatasolution.modules.notification.service.impl;

import afds.africadatasolution.domain.user.User;
import afds.africadatasolution.domain.user.UserRepository;
import afds.africadatasolution.modules.notification.NotificationPayload;
import afds.africadatasolution.modules.notification.service.NotificationService;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final UserRepository userRepository;
    private final FirebaseApp firebaseApp;

    public NotificationServiceImpl(UserRepository userRepository, Optional<FirebaseApp> firebaseApp) {
        this.userRepository = userRepository;
        this.firebaseApp = firebaseApp.orElse(null);
    }

    private boolean isEnabled() {
        return firebaseApp != null;
    }

    @Override
    public boolean sendToUser(UUID userId, NotificationPayload notification) {
        if (!isEnabled()) {
            log.debug("Firebase not enabled, skipping notification userId={}", userId);
            return false;
        }
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty() || user.get().getFcmToken() == null) {
            log.debug("User has no FCM token userId={}", userId);
            return false;
        }
        return sendToToken(user.get().getFcmToken(), notification);
    }

    @Override
    public boolean sendToToken(String token, NotificationPayload notification) {
        if (!isEnabled()) return false;

        Message.Builder builder = Message.builder()
                .setToken(token)
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(notification.title())
                        .setBody(notification.body())
                        .setImage(notification.imageUrl())
                        .build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setSound("default")
                                .setChannelId("africa_data_solutions")
                                .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder().setSound("default").setBadge(1).build())
                        .build());
        if (notification.data() != null) builder.putAllData(notification.data());

        try {
            String messageId = FirebaseMessaging.getInstance(firebaseApp).send(builder.build());
            log.info("Notification sent successfully messageId={} title={}", messageId, notification.title());
            return true;
        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                    || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("Invalid FCM token, clearing from database");
                clearInvalidToken(token);
            } else {
                log.error("Failed to send FCM notification error={}", e.getMessage());
            }
            return false;
        }
    }

    @Override
    public SendResult sendToMultipleUsers(List<UUID> userIds, NotificationPayload notification) {
        List<CompletableFuture<Boolean>> futures = userIds.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> sendToUser(id, notification)))
                .toList();
        long success = futures.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();
        return new SendResult((int) success, userIds.size() - (int) success);
    }

    @Transactional
    @Override
    public void saveToken(UUID userId, String fcmToken) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setFcmToken(fcmToken);
        log.info("FCM token saved for user userId={}", userId);
    }

    @Transactional
    @Override
    public void clearToken(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setFcmToken(null);
        log.info("FCM token cleared for user userId={}", userId);
    }

    @Transactional
    protected void clearInvalidToken(String token) {
        userRepository.clearFcmToken(token);
    }
}
