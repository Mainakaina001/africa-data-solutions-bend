package afds.africadatasolution.modules.outbox;

import afds.africadatasolution.domain.outbox.OutboxEvent;
import afds.africadatasolution.domain.outbox.OutboxEventRepository;
import afds.africadatasolution.domain.outbox.OutboxStatus;
import afds.africadatasolution.modules.notification.service.NotificationService;
import afds.africadatasolution.modules.notification.NotificationTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Outbox dispatcher — polls PENDING events and delivers them to the matching
 * consumer, retrying with exponential backoff up to a max-attempts cap.
 * Runs in-process on a fixed delay rather than as the separate
 * {@code worker:outbox} Node process; functionally equivalent for a
 * single-instance deployment. Mirrors backend/src/workers/outbox.ts.
 *
 * Consumers MUST be idempotent — outbox guarantees at-least-once delivery.
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final int BATCH_SIZE = 25;
    private static final int MAX_ATTEMPTS = 8;

    private final OutboxEventRepository outboxEventRepository;
    private final NotificationService notificationService;
    private final Map<String, Consumer<Map<String, Object>>> handlers;

    public OutboxDispatcher(OutboxEventRepository outboxEventRepository, NotificationService notificationService) {
        this.outboxEventRepository = outboxEventRepository;
        this.notificationService = notificationService;
        this.handlers = Map.of(
                "notification.wallet_funded", this::handleWalletFunded,
                "notification.data_purchase_success", this::handleDataPurchaseSuccess,
                "notification.data_purchase_failed", this::handleDataPurchaseFailed,
                "reconcile.airtime_order", payload -> { /* ack only — ReconciliationService owns the actual work */ },
                "reconcile.bill_payment", payload -> { /* ack only — ReconciliationService owns the actual work */ }
        );
    }

    @Scheduled(fixedDelay = 5000)
    public void processOnce() {
        List<OutboxEvent> due = outboxEventRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                OutboxStatus.PENDING, Instant.now(), PageRequest.of(0, BATCH_SIZE));

        for (OutboxEvent event : due) {
            if (outboxEventRepository.claim(event.getId()) == 0) continue;

            Consumer<Map<String, Object>> handler = handlers.get(event.getTopic());
            if (handler == null) {
                log.warn("No handler for outbox topic; marking FAILED topic={} id={}", event.getTopic(), event.getId());
                markFailed(event, "no_handler", true);
                continue;
            }

            try {
                handler.accept(event.getPayload());
                markCompleted(event);
            } catch (Exception e) {
                int attempts = event.getAttempts() + 1;
                boolean giveUp = attempts >= MAX_ATTEMPTS;
                log.warn("Outbox handler failed topic={} id={} attempts={} giveUp={} error={}",
                        event.getTopic(), event.getId(), attempts, giveUp, e.getMessage());
                markFailed(event, truncate(e.getMessage()), giveUp);
            }
        }
    }

    private void handleWalletFunded(Map<String, Object> payload) {
        UUID userId = UUID.fromString(String.valueOf(payload.get("userId")));
        BigDecimal amount = new BigDecimal(String.valueOf(payload.get("amount")));
        notificationService.sendToUser(userId, NotificationTemplates.walletFunded(amount));
    }

    private void handleDataPurchaseSuccess(Map<String, Object> payload) {
        UUID userId = UUID.fromString(String.valueOf(payload.get("userId")));
        String dataAmount = String.valueOf(payload.get("dataAmount"));
        String phone = String.valueOf(payload.get("phone"));
        notificationService.sendToUser(userId, NotificationTemplates.dataPurchaseSuccess(dataAmount, phone));
    }

    private void handleDataPurchaseFailed(Map<String, Object> payload) {
        UUID userId = UUID.fromString(String.valueOf(payload.get("userId")));
        String dataAmount = payload.get("dataAmount") != null ? String.valueOf(payload.get("dataAmount")) : "data";
        String reason = payload.get("reason") != null ? String.valueOf(payload.get("reason")) : "unknown";
        notificationService.sendToUser(userId, NotificationTemplates.dataPurchaseFailed(dataAmount, reason));
    }

    private void markCompleted(OutboxEvent event) {
        event.setStatus(OutboxStatus.COMPLETED);
        event.setProcessedAt(Instant.now());
        event.setAttempts(event.getAttempts() + 1);
        outboxEventRepository.save(event);
    }

    private void markFailed(OutboxEvent event, String error, boolean giveUp) {
        int attempts = event.getAttempts() + 1;
        event.setStatus(giveUp ? OutboxStatus.FAILED : OutboxStatus.PENDING);
        event.setAttempts(attempts);
        event.setLastError(error);
        event.setNextAttemptAt(Instant.now().plusMillis(nextDelayMs(attempts)));
        outboxEventRepository.save(event);
    }

    /** Exponential: 5s, 10s, 20s, 40s, ..., capped at 1h. */
    private long nextDelayMs(int attempts) {
        return Math.min(5000L * (1L << Math.min(attempts, 20)), 60 * 60 * 1000L);
    }

    private String truncate(String message) {
        if (message == null) return null;
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
