package de.shellnuts.messaging;

import de.shellnuts.domain.event.OrderSubmittedEvent;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;

@ApplicationScoped
public class TresenEventProducer {

    @Inject
    @Channel("tresen-orders-out")
    MutinyEmitter<String> emitter;

    @Inject
    EventCodec eventCodec;

    public Uni<Void> sendSubmitted(OrderSubmittedEvent event) {
        String payload = eventCodec.toJson(event);
        Message<String> message = Message.of(payload)
                .addMetadata(OutgoingKafkaRecordMetadata.builder()
                        .withKey(event != null && event.orderId() != null ? event.orderId().toString() : "unknown")
                        .build());
        return emitter.sendMessage(message);
    }
}
