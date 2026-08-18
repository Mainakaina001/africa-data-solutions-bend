package afds.africadatasolution.modules.outbox.service.impl;

import afds.africadatasolution.domain.outbox.OutboxEvent;
import afds.africadatasolution.domain.outbox.OutboxEventRepository;
import afds.africadatasolution.modules.outbox.service.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class OutboxServiceImpl implements OutboxService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxServiceImpl(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    @Override
    public void enqueue(String topic, String aggregateId, Map<String, Object> payload) {
        OutboxEvent event = new OutboxEvent();
        event.setTopic(topic);
        event.setAggregateId(aggregateId);
        event.setPayload(payload);
        outboxEventRepository.save(event);
    }
}
