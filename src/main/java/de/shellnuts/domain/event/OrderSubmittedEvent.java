package de.shellnuts.domain.event;

import java.time.Instant;
import java.util.List;

public record OrderSubmittedEvent(Long orderId, String customerName, Instant createdAt, List<OrderLine> items) {
    public record OrderLine(Long coffeeId, int quantity) {
    }
}
