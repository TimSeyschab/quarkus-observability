package de.shellnuts.observability;

import de.shellnuts.domain.model.CustomerOrder;
import de.shellnuts.domain.model.OrderItem;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class CoffeeShopMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter submittedOrdersCounter;
    private final Counter readyOrdersCounter;
    private final Counter pickedUpOrdersCounter;
    private final Counter beveragesSoldCounter;
    private final Counter revenueCounterCents;
    private final Counter kitchenProcessingFailuresCounter;
    private final Timer kitchenProcessingTimer;
    private final Timer customerWaitToReadyTimer;
    private final Timer customerLeadTimeTimer;
    private final AtomicInteger kitchenBackpressureOrders = new AtomicInteger(0);
    private final Map<Long, Long> waitingOrderStartMillis = new ConcurrentHashMap<>();

    @Inject
    public CoffeeShopMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.submittedOrdersCounter = meterRegistry.counter("coffeeshop_orders_submitted_total");
        this.readyOrdersCounter = meterRegistry.counter("coffeeshop_orders_ready_total");
        this.pickedUpOrdersCounter = meterRegistry.counter("coffeeshop_orders_picked_up_total");
        this.beveragesSoldCounter = meterRegistry.counter("coffeeshop_beverages_sold_total");
        this.revenueCounterCents = meterRegistry.counter("coffeeshop_revenue_cents_total");
        this.kitchenProcessingFailuresCounter = meterRegistry.counter("coffeeshop_kitchen_processing_failures_total");
        this.kitchenProcessingTimer = Timer.builder("coffeeshop_kitchen_processing_seconds")
                .description("Kitchen order processing time")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.customerWaitToReadyTimer = Timer.builder("coffeeshop_customer_wait_to_ready_seconds")
                .description("Customer wait time from order creation until kitchen marks order ready")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.customerLeadTimeTimer = Timer.builder("coffeeshop_customer_total_lead_time_seconds")
                .description("Lead time from order creation until pickup")
                .publishPercentileHistogram()
                .register(meterRegistry);

        Gauge.builder("coffeeshop_kitchen_backpressure_orders", kitchenBackpressureOrders, AtomicInteger::get)
                .description("Current number of orders waiting in kitchen")
                .register(meterRegistry);
        Gauge.builder("coffeeshop_current_wait_seconds_avg", this, CoffeeShopMetrics::currentAverageWaitSeconds)
                .description("Average current wait time for open kitchen orders")
                .register(meterRegistry);
        Gauge.builder("coffeeshop_kitchen_backpressure_seconds_estimate", this, CoffeeShopMetrics::estimatedBackpressureSeconds)
                .description("Estimated seconds to clear current kitchen backlog")
                .register(meterRegistry);
    }

    public void onOrderSubmitted(CustomerOrder order) {
        if (order == null) {
            return;
        }
        submittedOrdersCounter.increment();

        if (order.id != null) {
            waitingOrderStartMillis.put(order.id, toEpochMillis(order.createdAt));
            kitchenBackpressureOrders.incrementAndGet();
        }

        long orderRevenueCents = 0;
        int soldBeverages = 0;
        for (OrderItem item : order.items) {
            if (item == null) {
                continue;
            }
            int quantity = Math.max(0, item.quantity);
            if (quantity == 0) {
                continue;
            }
            soldBeverages += quantity;
            beveragesSoldCounter.increment(quantity);
            String coffeeName = item.coffee != null ? tagValue(item.coffee.name) : "unknown";
            meterRegistry.counter("coffeeshop_beverages_sold_by_coffee_total", "coffee", coffeeName).increment(quantity);
            int priceCents = item.coffee != null ? Math.max(0, item.coffee.priceCents) : 0;
            orderRevenueCents += (long) quantity * priceCents;
        }

        if (orderRevenueCents > 0) {
            revenueCounterCents.increment(orderRevenueCents);
        }
        if (soldBeverages > 0) {
            meterRegistry.counter("coffeeshop_beverages_per_order_total").increment(soldBeverages);
        }
    }

    public void onOrderReady(CustomerOrder order) {
        if (order == null) {
            return;
        }
        readyOrdersCounter.increment();

        if (order.id == null) {
            return;
        }

        Long startMillis = waitingOrderStartMillis.remove(order.id);
        if (startMillis == null) {
            return;
        }

        kitchenBackpressureOrders.updateAndGet(current -> Math.max(0, current - 1));
        long waitMillis = Math.max(0L, System.currentTimeMillis() - startMillis);
        customerWaitToReadyTimer.record(waitMillis, TimeUnit.MILLISECONDS);
    }

    public void onOrderPickedUp(CustomerOrder order) {
        if (order == null) {
            return;
        }
        pickedUpOrdersCounter.increment();

        long leadMillis = Math.max(0L, System.currentTimeMillis() - toEpochMillis(order.createdAt));
        customerLeadTimeTimer.record(leadMillis, TimeUnit.MILLISECONDS);
    }

    public void recordKitchenProcessing(Duration processingDuration) {
        if (processingDuration == null || processingDuration.isNegative()) {
            return;
        }
        kitchenProcessingTimer.record(processingDuration);
    }

    public void recordKitchenProcessingFailure() {
        kitchenProcessingFailuresCounter.increment();
    }

    private double currentAverageWaitSeconds() {
        if (waitingOrderStartMillis.isEmpty()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        long totalWaitMillis = 0;
        int openOrders = 0;
        for (Long startedAtMillis : waitingOrderStartMillis.values()) {
            if (startedAtMillis == null) {
                continue;
            }
            totalWaitMillis += Math.max(0L, now - startedAtMillis);
            openOrders++;
        }

        if (openOrders == 0) {
            return 0;
        }
        return (totalWaitMillis / 1000.0) / openOrders;
    }

    private double estimatedBackpressureSeconds() {
        double meanKitchenProcessingSeconds = kitchenProcessingTimer.mean(TimeUnit.SECONDS);
        if (!Double.isFinite(meanKitchenProcessingSeconds) || meanKitchenProcessingSeconds <= 0) {
            return 0;
        }
        return kitchenBackpressureOrders.get() * meanKitchenProcessingSeconds;
    }

    private static long toEpochMillis(Instant instant) {
        return instant != null ? instant.toEpochMilli() : System.currentTimeMillis();
    }

    private static String tagValue(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim();
    }
}
