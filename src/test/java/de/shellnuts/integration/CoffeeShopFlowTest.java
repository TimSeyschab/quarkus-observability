package de.shellnuts.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.shellnuts.domain.event.OrderReadyEvent;
import de.shellnuts.domain.event.OrderSubmittedEvent;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@QuarkusTestResource(InMemoryMessagingTestResource.class)
class CoffeeShopFlowTest {

    @Inject
    @Connector("smallrye-in-memory")
    InMemoryConnector inMemoryConnector;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void shouldCreateOrderAndPublishSubmittedEvent() throws Exception {
        long orderId = createOrder();

        OrderSubmittedEvent submittedEvent = findSubmittedEvent(orderId);
        assertEquals(orderId, submittedEvent.orderId());
        assertEquals("Customer Integration", submittedEvent.customerName());
        assertNotNull(submittedEvent.createdAt());
    }

    @Test
    void shouldProcessKitchenEventAndMoveToPickedUpAfterExplicitPickup() throws Exception {
        long orderId = createOrder();

        Message<String> submittedMessage = Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .until(() -> findSubmittedMessage(orderId), message -> message != null);
        inMemoryConnector.source("kueche-orders-in").send(submittedMessage.getPayload());

        OrderReadyEvent readyEvent = Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .until(() -> findReadyEvent(orderId), event -> event != null);

        inMemoryConnector.source("tresen-ready-in").send(objectMapper.writeValueAsString(readyEvent));

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                given()
                        .when().get("/coffeeshop/orders/{orderId}", orderId)
                        .then()
                        .statusCode(200)
                        .body("status", equalTo("FERTIG"))
        );

        given()
                .when().get("/coffeeshop/orders/{orderId}", orderId)
                .then()
                .statusCode(200)
                .body("status", equalTo("FERTIG"));

        given()
                .when().post("/coffeeshop/orders/{orderId}/pickup", orderId)
                .then()
                .statusCode(200)
                .body("status", equalTo("ABGEHOLT"));
    }

    @Test
    void shouldRejectPickupBeforeOrderIsReady() {
        long orderId = createOrder();

        given()
                .when().post("/coffeeshop/orders/{orderId}/pickup", orderId)
                .then()
                .statusCode(400)
                .body("detail", equalTo("order is not ready for pickup: " + orderId));
    }

    @Test
    void shouldRejectInvalidOrderPayloadWithProblemDetails() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "customerName": "",
                          "items": [
                            {"coffeeId": 1, "quantity": 0}
                          ]
                        }
                        """)
                .when().post("/coffeeshop/orders")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("title", equalTo("Constraint Violation"))
                .body("violations.size()", greaterThan(0));
    }

    private long createOrder() {
        Number orderId = given()
                .contentType("application/json")
                .body("""
                        {
                          "customerName": "Customer Integration",
                          "items": [
                            {"coffeeId": 1, "quantity": 1},
                            {"coffeeId": 2, "quantity": 2}
                          ]
                        }
                        """)
                .when().post("/coffeeshop/orders")
                .then()
                .statusCode(201)
                .body("status", equalTo("ANGENOMMEN"))
                .extract()
                .path("id");
        return orderId.longValue();
    }

    private Message<String> findSubmittedMessage(long orderId) throws Exception {
        List<? extends Message<?>> messages = inMemoryConnector.sink("tresen-orders-out").received();
        for (Message<?> message : messages) {
            String payload = message.getPayload().toString();
            OrderSubmittedEvent event = objectMapper.readValue(payload, OrderSubmittedEvent.class);
            if (event.orderId() == orderId) {
                return Message.of(payload);
            }
        }
        return null;
    }

    private OrderSubmittedEvent findSubmittedEvent(long orderId) throws Exception {
        Message<String> message = Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .until(() -> findSubmittedMessage(orderId), msg -> msg != null);
        return objectMapper.readValue(message.getPayload(), OrderSubmittedEvent.class);
    }

    private OrderReadyEvent findReadyEvent(long orderId) throws Exception {
        List<? extends Message<?>> messages = inMemoryConnector.sink("kueche-orders-out").received();
        for (Message<?> message : messages) {
            OrderReadyEvent event = objectMapper.readValue(message.getPayload().toString(), OrderReadyEvent.class);
            if (event.orderId() == orderId && event.readyAt().isBefore(Instant.now().plusSeconds(1))) {
                return event;
            }
        }
        return null;
    }
}
