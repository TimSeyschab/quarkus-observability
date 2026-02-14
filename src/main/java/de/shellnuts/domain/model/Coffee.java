package de.shellnuts.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "coffee")
public class Coffee extends PanacheEntity {
    public String name;
    public int priceCents;
}
