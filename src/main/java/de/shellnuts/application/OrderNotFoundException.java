package de.shellnuts.application;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(Long orderId) {
        super("order not found: " + orderId);
    }
}
