package de.shellnuts.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_order")
public class CustomerOrder extends PanacheEntity {
    public String customerName;
    public Instant createdAt;

    @Enumerated(EnumType.STRING)
    public OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<OrderItem> items = new ArrayList<>();

    public void addItem(Coffee coffee, int quantity) {
        OrderItem item = new OrderItem();
        item.order = this;
        item.coffee = coffee;
        item.quantity = quantity;
        items.add(item);
    }

    public void markReady() {
        status = OrderStatus.FERTIG;
    }

    public void markPickedUp() {
        status = OrderStatus.ABGEHOLT;
    }
}
