package de.shellnuts.api;

import de.shellnuts.domain.model.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class OrderApiModels {
    private OrderApiModels() {
    }

    public record CreateOrderRequest(
            @NotBlank(message = "customerName is required")
            @Size(max = 120, message = "customerName length must be <= 120")
            String customerName,
            @NotNull(message = "items are required")
            @Size(min = 1, max = 20, message = "items must contain between 1 and 20 entries")
            List<@NotNull(message = "item is required") @Valid OrderItemRequest> items) {
    }

    public record OrderItemRequest(
            @NotNull(message = "coffeeId is required")
            @Positive(message = "coffeeId must be > 0")
            Long coffeeId,
            @NotNull(message = "quantity is required")
            @Positive(message = "quantity must be > 0")
            Integer quantity) {
    }

    public record OrderResponse(Long id, String customerName, Instant createdAt, OrderStatus status,
                                List<OrderResponseItem> items) {
    }

    public record OrderResponseItem(Long coffeeId, String coffeeName, int priceCents, int quantity) {
    }

    public record ProblemDetails(
            String type,
            String title,
            int status,
            String detail,
            String instance,
            Map<String, String> violations) {
    }
}
