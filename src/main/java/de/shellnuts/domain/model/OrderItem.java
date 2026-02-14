package de.shellnuts.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_item")
public class OrderItem extends PanacheEntity {
    @ManyToOne
    @JoinColumn(name = "order_id")
    public CustomerOrder order;

    @ManyToOne
    @JoinColumn(name = "coffee_id")
    public Coffee coffee;

    public int quantity;
}
