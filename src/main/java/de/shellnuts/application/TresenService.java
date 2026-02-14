package de.shellnuts.application;

import de.shellnuts.api.OrderApiModels;
import de.shellnuts.domain.event.OrderSubmittedEvent;
import de.shellnuts.domain.model.Coffee;
import de.shellnuts.domain.model.CustomerOrder;
import de.shellnuts.domain.model.OrderStatus;
import de.shellnuts.observability.CoffeeShopMetrics;
import de.shellnuts.persistence.CoffeeRepository;
import de.shellnuts.persistence.CustomerOrderRepository;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class TresenService {

    private static final Logger LOG = Logger.getLogger(TresenService.class);

    @Inject
    CoffeeRepository coffeeRepository;

    @Inject
    CustomerOrderRepository orderRepository;

    @Inject
    OrderOutboxService orderOutboxService;

    @Inject
    CoffeeShopMetrics coffeeShopMetrics;

    @Transactional
    @WithSpan("tresen.take-order")
    public OrderApiModels.OrderResponse takeOrder(OrderApiModels.CreateOrderRequest request) {
        CustomerOrder order = new CustomerOrder();
        order.customerName = request.customerName().trim();
        order.createdAt = Instant.now();
        order.status = OrderStatus.ANGENOMMEN;

        for (OrderApiModels.OrderItemRequest itemRequest : request.items()) {
            Coffee coffee = coffeeRepository.findById(itemRequest.coffeeId());
            if (coffee == null) {
                throw new OrderValidationException("coffeeId not found: " + itemRequest.coffeeId());
            }
            order.addItem(coffee, itemRequest.quantity());
        }

        orderRepository.persist(order);
        int beverages = totalBeverages(order);
        long revenueCents = totalRevenueCents(order);
        annotateSpan(order, beverages, revenueCents);
        coffeeShopMetrics.onOrderSubmitted(order);
        LOG.infof(
                "event=order_submitted order_id=%d customer_name=\"%s\" customer_id=%d item_count=%d beverage_count=%d revenue_cents=%d coffee_ids=\"%s\"",
                order.id, order.customerName, order.id, order.items.size(), beverages, revenueCents, coffeeIds(order));
        orderOutboxService.enqueueOrderSubmitted(toSubmittedEvent(order));
        return toResponse(order);
    }

    @Transactional
    @WithSpan("tresen.get-order-status")
    public OrderApiModels.OrderResponse getOrderStatus(Long orderId) {
        CustomerOrder order = orderRepository.findById(orderId);
        if (order == null) {
            throw new OrderNotFoundException(orderId);
        }

        annotateSpan(order, totalBeverages(order), totalRevenueCents(order));
        return toResponse(order);
    }

    @Transactional
    @WithSpan("tresen.mark-order-ready")
    public void markOrderReady(Long orderId) {
        if (orderId == null) {
            return;
        }

        CustomerOrder order = orderRepository.findById(orderId);
        if (order == null) {
            LOG.warnf("event=order_ready_ignored order_id=%d reason=missing_order", orderId);
            return;
        }
        if (order.status == OrderStatus.ABGEHOLT) {
            LOG.warnf("event=order_ready_ignored order_id=%d reason=already_picked_up", order.id);
            return;
        }
        if (order.status != OrderStatus.ANGENOMMEN) {
            return;
        }
        order.markReady();
        coffeeShopMetrics.onOrderReady(order);
        annotateSpan(order, totalBeverages(order), totalRevenueCents(order));
        LOG.infof("event=order_ready order_id=%d customer_name=\"%s\" customer_id=%d wait_to_ready_seconds=%.3f",
                order.id, order.customerName, order.id, secondsSinceCreated(order));
    }

    @Transactional
    @WithSpan("tresen.mark-order-picked-up")
    public void markOrderPickedUp(Long orderId) {
        if (orderId == null) {
            throw new OrderValidationException("orderId is required");
        }

        CustomerOrder order = orderRepository.findById(orderId);
        if (order == null) {
            throw new OrderNotFoundException(orderId);
        }

        if (order.status == OrderStatus.ABGEHOLT) {
            return;
        }
        if (order.status != OrderStatus.FERTIG) {
            throw new OrderValidationException("order is not ready for pickup: " + orderId);
        }

        order.markPickedUp();
        coffeeShopMetrics.onOrderPickedUp(order);
        annotateSpan(order, totalBeverages(order), totalRevenueCents(order));
        LOG.infof("event=order_picked_up order_id=%d customer_name=\"%s\" customer_id=%d total_lead_time_seconds=%.3f",
                order.id, order.customerName, order.id, secondsSinceCreated(order));
    }

    private OrderApiModels.OrderResponse toResponse(CustomerOrder order) {
        List<OrderApiModels.OrderResponseItem> items = order.items.stream()
                .map(item -> new OrderApiModels.OrderResponseItem(
                        item.coffee.id,
                        item.coffee.name,
                        item.coffee.priceCents,
                        item.quantity))
                .collect(Collectors.toList());

        return new OrderApiModels.OrderResponse(
                order.id,
                order.customerName,
                order.createdAt,
                order.status,
                items);
    }

    private OrderSubmittedEvent toSubmittedEvent(CustomerOrder order) {
        List<OrderSubmittedEvent.OrderLine> lines = order.items.stream()
                .map(item -> new OrderSubmittedEvent.OrderLine(item.coffee.id, item.quantity))
                .collect(Collectors.toList());

        return new OrderSubmittedEvent(order.id, order.customerName, order.createdAt, lines);
    }

    private int totalBeverages(CustomerOrder order) {
        return order.items.stream()
                .mapToInt(item -> Math.max(0, item.quantity))
                .sum();
    }

    private long totalRevenueCents(CustomerOrder order) {
        return order.items.stream()
                .mapToLong(item -> (long) Math.max(0, item.quantity) * Math.max(0, item.coffee.priceCents))
                .sum();
    }

    private String coffeeIds(CustomerOrder order) {
        return order.items.stream()
                .map(item -> item.coffee != null && item.coffee.id != null ? item.coffee.id.toString() : "unknown")
                .distinct()
                .collect(Collectors.joining(","));
    }

    private double secondsSinceCreated(CustomerOrder order) {
        if (order.createdAt == null) {
            return 0;
        }
        long millis = Math.max(0L, System.currentTimeMillis() - order.createdAt.toEpochMilli());
        return millis / 1000.0;
    }

    private void annotateSpan(CustomerOrder order, int beverages, long revenueCents) {
        Span span = Span.current();
        if (!span.getSpanContext().isValid()) {
            return;
        }
        if (order.id != null) {
            span.setAttribute("coffee.order.id", order.id);
        }
        span.setAttribute("coffee.order.customer", order.customerName);
        span.setAttribute("coffee.order.status", order.status.name());
        span.setAttribute("coffee.order.items", order.items.size());
        span.setAttribute("coffee.order.beverages", beverages);
        span.setAttribute("coffee.order.revenue_cents", revenueCents);
    }
}
