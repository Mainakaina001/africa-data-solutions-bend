package afds.africadatasolution.modules.outbox.service;

import java.util.Map;

/**
 * Transactional outbox — enqueue side. The event is written in the SAME
 * database transaction as the wallet movement that triggered it, guaranteeing
 * at-least-once delivery even if the process crashes right after commit.
 * Dispatched by {@code OutboxDispatcher}. Mirrors backend/src/services/outbox.service.ts.
 */
public interface OutboxService {

    void enqueue(String topic, String aggregateId, Map<String, Object> payload);
}
