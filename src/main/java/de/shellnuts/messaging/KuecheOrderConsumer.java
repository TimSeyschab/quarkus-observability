package de.shellnuts.messaging;

import de.shellnuts.domain.event.OrderReadyEvent;
import de.shellnuts.domain.event.OrderSubmittedEvent;
import de.shellnuts.observability.CoffeeShopMetrics;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class KuecheOrderConsumer {

    private static final Logger LOG = Logger.getLogger(KuecheOrderConsumer.class);

    @Inject
    EventCodec eventCodec;

    @Inject
    KuecheEventProducer kuecheEventProducer;

    @Inject
    CoffeeShopMetrics coffeeShopMetrics;

    @ConfigProperty(name = "kueche.processing.delay.min-seconds", defaultValue = "2")
    int minDelaySeconds;

    @ConfigProperty(name = "kueche.processing.delay.max-seconds", defaultValue = "20")
    int maxDelaySeconds;

    @Incoming("kueche-orders-in")
    @Blocking
    @WithSpan("kueche.process-order")
    public void processOrder(String payload) {
        long startedAt = System.nanoTime();
        try {
            OrderSubmittedEvent event = eventCodec.fromJson(payload, OrderSubmittedEvent.class);
            annotateSpan(event);
            int delaySeconds = sleepProcessingTime();
            Span.current().setAttribute("coffee.kitchen.delay_seconds", delaySeconds);
            kuecheEventProducer.sendReady(new OrderReadyEvent(event.orderId(), Instant.now()));
            LOG.infof(
                    "event=kueche_order_processed order_id=%d customer_name=\"%s\" customer_id=%d delay_seconds=%d coffee_ids=\"%s\"",
                    event.orderId(), event.customerName(), event.orderId(), delaySeconds, coffeeIds(event));
        } catch (RuntimeException e) {
            coffeeShopMetrics.recordKitchenProcessingFailure();
            LOG.errorf(e, "event=kueche_order_processing_failed payload_length=%d", payload != null ? payload.length() : 0);
            throw e;
        } finally {
            coffeeShopMetrics.recordKitchenProcessing(Duration.ofNanos(System.nanoTime() - startedAt));
        }
    }

    int sleepProcessingTime() {
        int min = Math.max(0, minDelaySeconds);
        int max = Math.max(min, maxDelaySeconds);
        int delaySeconds = ThreadLocalRandom.current().nextInt(min, max + 1);

        try {
            Thread.sleep(delaySeconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return delaySeconds;
    }

    private void annotateSpan(OrderSubmittedEvent event) {
        Span span = Span.current();
        if (!span.getSpanContext().isValid() || event == null) {
            return;
        }

        if (event.orderId() != null) {
            span.setAttribute("coffee.order.id", event.orderId());
        }
        if (event.customerName() != null) {
            span.setAttribute("coffee.order.customer", event.customerName());
        }
        int beverages = event.items() == null ? 0 : event.items().stream()
                .mapToInt(line -> Math.max(0, line.quantity()))
                .sum();
        span.setAttribute("coffee.order.beverages", beverages);
    }

    private String coffeeIds(OrderSubmittedEvent event) {
        if (event == null || event.items() == null || event.items().isEmpty()) {
            return "unknown";
        }
        return event.items().stream()
                .map(line -> line.coffeeId() != null ? line.coffeeId().toString() : "unknown")
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("unknown");
    }
}
