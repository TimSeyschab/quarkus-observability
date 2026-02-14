package de.shellnuts.application;

import de.shellnuts.domain.event.OrderSubmittedEvent;
import de.shellnuts.domain.model.OutboxEvent;
import de.shellnuts.messaging.EventCodec;
import de.shellnuts.persistence.OutboxEventRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;

import java.time.Instant;

@ApplicationScoped
public class OrderOutboxService {

    private static final String DISPATCH_SYNC_KEY = "outbox.dispatch.sync.registered";

    @Inject
    OutboxEventRepository outboxEventRepository;

    @Inject
    EventCodec eventCodec;

    @Inject
    OrderOutboxPublisher orderOutboxPublisher;

    @Inject
    TransactionSynchronizationRegistry txSyncRegistry;

    @Transactional
    public void enqueueOrderSubmitted(OrderSubmittedEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }

        String eventKey = submittedEventKey(event.orderId());
        if (outboxEventRepository.findByEventKey(eventKey) != null) {
            return;
        }

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.eventKey = eventKey;
        outboxEvent.eventType = "ORDER_SUBMITTED";
        outboxEvent.createdAt = Instant.now();
        outboxEvent.nextAttemptAt = outboxEvent.createdAt;
        outboxEvent.attempts = 0;
        outboxEvent.payload = eventCodec.toJson(event);
        outboxEventRepository.persist(outboxEvent);
        registerPostCommitDispatch();
    }

    public static String submittedEventKey(Long orderId) {
        return "ORDER_SUBMITTED:" + orderId;
    }

    private void registerPostCommitDispatch() {
        if (txSyncRegistry.getTransactionStatus() != Status.STATUS_ACTIVE) {
            return;
        }
        if (txSyncRegistry.getResource(DISPATCH_SYNC_KEY) != null) {
            return;
        }

        txSyncRegistry.putResource(DISPATCH_SYNC_KEY, Boolean.TRUE);
        txSyncRegistry.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
            }

            @Override
            public void afterCompletion(int status) {
                if (status == Status.STATUS_COMMITTED) {
                    orderOutboxPublisher.dispatchNow();
                }
            }
        });
    }
}
