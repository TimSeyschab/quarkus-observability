package de.shellnuts.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "outbox_event",
        indexes = {
                @Index(name = "idx_outbox_due", columnList = "published_at,next_attempt_at,created_at"),
                @Index(name = "idx_outbox_created", columnList = "created_at")
        })
public class OutboxEvent extends PanacheEntity {
    @Column(name = "event_key", nullable = false, unique = true, length = 128)
    public String eventKey;

    @Column(name = "event_type", nullable = false, length = 64)
    public String eventType;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    public Instant nextAttemptAt;

    @Column(nullable = false)
    public int attempts;

    @Column(length = 4000)
    public String payload;

    @Column(name = "published_at")
    public Instant publishedAt;

    @Column(name = "last_error", length = 512)
    public String lastError;

    public boolean isPublished() {
        return publishedAt != null;
    }
}
