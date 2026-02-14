package de.shellnuts.persistence;

import de.shellnuts.domain.model.CustomerOrder;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CustomerOrderRepository implements PanacheRepository<CustomerOrder> {
}
