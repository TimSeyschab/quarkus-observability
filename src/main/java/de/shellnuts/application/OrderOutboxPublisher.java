package de.shellnuts.application;

import de.shellnuts.domain.event.OrderSubmittedEvent;
import de.shellnuts.domain.model.OutboxEvent;
import de.shellnuts.messaging.EventCodec;
import de.shellnuts.messaging.TresenEventProducer;
import de.shellnuts.persistence.OutboxEventRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@ApplicationScoped
public class OrderOutboxPublisher {

    private static final Logger LOG = Logger.getLogger(OrderOutboxPublisher.class);
    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRY_BACKOFF_SECONDS = 60;
    private static final int MAX_ERROR_LENGTH = 512;
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

    @Inject
    OutboxEventRepository outboxEventRepository;

    @Inject
    EventCodec eventCodec;

    @Inject
    TresenEventProducer tresenEventProducer;

    @ConfigProperty(name = "outbox.dispatch.min-seconds", defaultValue = "1")
    int minPollSeconds;

    @ConfigProperty(name = "outbox.dispatch.max-seconds", defaultValue = "15")
    int maxPollSeconds;

    private volatile Instant nextPollAt = Instant.EPOCH;
    private volatile int currentPollSeconds = 1;
    private final ReentrantLock dispatchLock = new ReentrantLock();

    @Scheduled(every = "{outbox.dispatch.tick:1s}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Transactional
    void publishDueEvents() {
        runDispatch(false);
    }

    @Transactional
    public void dispatchNow() {
        nextPollAt = Instant.EPOCH;
        currentPollSeconds = Math.max(1, minPollSeconds);
        runDispatch(true);
    }

    private void runDispatch(boolean forced) {
        if (!dispatchLock.tryLock()) {
            return;
        }
        try {
            dispatchOnce(forced);
        } finally {
            dispatchLock.unlock();
        }
    }

    private void dispatchOnce(boolean forced) {
        Instant now = Instant.now();
        if (!forced && now.isBefore(nextPollAt)) {
            return;
        }

        List<OutboxEvent> dueEvents = outboxEventRepository.claimDueEvents(BATCH_SIZE);
        if (dueEvents.isEmpty()) {
            scheduleNextPollWithBackoff(now);
            return;
        }

        scheduleNextPollAtMinInterval(now);
        for (OutboxEvent outboxEvent : dueEvents) {
            publish(outboxEvent);
        }
    }

    private void publish(OutboxEvent outboxEvent) {
        if (outboxEvent == null || outboxEvent.isPublished()) {
            return;
        }

        try {
            OrderSubmittedEvent event = eventCodec.fromJson(outboxEvent.payload, OrderSubmittedEvent.class);
            tresenEventProducer.sendSubmitted(event).await().atMost(SEND_TIMEOUT);
            outboxEvent.publishedAt = Instant.now();
            outboxEvent.lastError = null;
        } catch (Exception e) {
            outboxEvent.attempts = outboxEvent.attempts + 1;
            long delaySeconds = Math.min(MAX_RETRY_BACKOFF_SECONDS, 1L << Math.min(6, outboxEvent.attempts));
            outboxEvent.nextAttemptAt = Instant.now().plusSeconds(delaySeconds);
            outboxEvent.lastError = truncateError(e.getClass().getSimpleName() + ": " + e.getMessage());
            LOG.warnf(e, "event=outbox_publish_failed event_id=%d attempts=%d next_attempt_at=%s",
                    outboxEvent.id, outboxEvent.attempts, outboxEvent.nextAttemptAt);
        }
    }

    private void scheduleNextPollWithBackoff(Instant now) {
        int maxSeconds = Math.max(minPollSeconds, maxPollSeconds);
        currentPollSeconds = Math.min(maxSeconds, Math.max(minPollSeconds, currentPollSeconds * 2));
        nextPollAt = now.plusSeconds(currentPollSeconds);
    }

    private void scheduleNextPollAtMinInterval(Instant now) {
        currentPollSeconds = Math.max(1, minPollSeconds);
        nextPollAt = now.plusSeconds(currentPollSeconds);
    }

    private static String truncateError(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > MAX_ERROR_LENGTH ? value.substring(0, MAX_ERROR_LENGTH) : value;
    }
}
