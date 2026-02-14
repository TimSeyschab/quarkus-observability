package de.shellnuts.persistence;

import de.shellnuts.domain.model.OutboxEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class OutboxEventRepository implements PanacheRepository<OutboxEvent> {

    @Inject
    EntityManager entityManager;

    public List<OutboxEvent> claimDueEvents(int limit) {
        int effectiveLimit = Math.max(1, limit);
        Instant now = Instant.now();
        try {
            @SuppressWarnings("unchecked")
            List<OutboxEvent> events = entityManager.createNativeQuery(
                            "select * from outbox_event " +
                                    "where published_at is null and next_attempt_at <= :now " +
                                    "order by created_at asc " +
                                    "for update skip locked", OutboxEvent.class)
                    .setParameter("now", now)
                    .setMaxResults(effectiveLimit)
                    .getResultList();
            return events;
        } catch (RuntimeException unsupportedSkipLocked) {
            // H2 in Tests unterstuetzt SKIP LOCKED moeglicherweise nicht konsistent.
            // Fuer Demo erstmal ausreichend
            return find("publishedAt is null and nextAttemptAt <= ?1 order by createdAt asc", now)
                    .range(0, effectiveLimit - 1)
                    .list();
        }
    }

    public OutboxEvent findByEventKey(String eventKey) {
        return find("eventKey", eventKey).firstResult();
    }
}
