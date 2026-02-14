package de.shellnuts.persistence;

import de.shellnuts.domain.model.Coffee;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

import java.util.List;

@Startup
@ApplicationScoped
public class CoffeeSeeder {

    private static final List<CoffeeSeed> COFFEES = List.of(
            new CoffeeSeed("Espresso", 250),
            new CoffeeSeed("Americano", 300),
            new CoffeeSeed("Cappuccino", 350),
            new CoffeeSeed("Latte", 380),
            new CoffeeSeed("Flat White", 360)
    );

    @Transactional
    void onStart(@Observes StartupEvent ignored) {
        if (Coffee.count() > 0) {
            return;
        }

        for (CoffeeSeed seed : COFFEES) {
            Coffee coffee = new Coffee();
            coffee.name = seed.name;
            coffee.priceCents = seed.priceCents;
            coffee.persist();
        }
    }

    private record CoffeeSeed(String name, int priceCents) {
    }
}
