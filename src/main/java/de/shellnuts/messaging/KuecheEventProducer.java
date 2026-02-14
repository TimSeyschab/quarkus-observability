package de.shellnuts.messaging;

import de.shellnuts.domain.event.OrderReadyEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class KuecheEventProducer {

    @Inject
    @Channel("kueche-orders-out")
    Emitter<String> emitter;

    @Inject
    EventCodec eventCodec;

    public void sendReady(OrderReadyEvent event) {
        emitter.send(eventCodec.toJson(event));
    }
}
