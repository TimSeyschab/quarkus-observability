package de.shellnuts.persistence;

import de.shellnuts.domain.model.Coffee;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CoffeeRepository implements PanacheRepository<Coffee> {
}
