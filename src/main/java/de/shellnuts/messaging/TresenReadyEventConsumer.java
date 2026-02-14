package de.shellnuts.messaging;

import de.shellnuts.application.TresenService;
import de.shellnuts.domain.event.OrderReadyEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class TresenReadyEventConsumer {

    private static final Logger LOG = Logger.getLogger(TresenReadyEventConsumer.class);

    @Inject
    EventCodec eventCodec;

    @Inject
    TresenService tresenService;

    @Incoming("tresen-ready-in")
    @WithSpan("tresen.process-ready-event")
    public void processReadyEvent(String payload) {
        try {
            OrderReadyEvent event = eventCodec.fromJson(payload, OrderReadyEvent.class);
            annotateSpan(event);
            LOG.infof("event=ready_event_received order_id=%d customer_id=%d ready_at=%s",
                    event.orderId(), event.orderId(), event.readyAt());
            tresenService.markOrderReady(event.orderId());
        } catch (RuntimeException e) {
            LOG.errorf(e, "event=ready_event_processing_failed payload_length=%d", payload != null ? payload.length() : 0);
            throw e;
        }
    }

    private void annotateSpan(OrderReadyEvent event) {
        Span span = Span.current();
        if (!span.getSpanContext().isValid() || event == null) {
            return;
        }
        if (event.orderId() != null) {
            span.setAttribute("coffee.order.id", event.orderId());
        }
        if (event.readyAt() != null) {
            span.setAttribute("coffee.order.ready_at", event.readyAt().toString());
        }
    }
}
