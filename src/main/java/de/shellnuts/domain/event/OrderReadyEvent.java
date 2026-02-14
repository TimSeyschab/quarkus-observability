package de.shellnuts.domain.event;

import java.time.Instant;

public record OrderReadyEvent(Long orderId, Instant readyAt) {
}
