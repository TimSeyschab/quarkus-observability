package de.shellnuts.api;

import de.shellnuts.application.TresenService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/coffeeshop")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CoffeeShopResource {

    @Inject
    TresenService tresenService;

    @POST
    @Path("/orders")
    public Response createOrder(@NotNull @Valid OrderApiModels.CreateOrderRequest request) {
        return Response.status(Response.Status.CREATED).entity(tresenService.takeOrder(request)).build();
    }

    @GET
    @Path("/orders/{orderId}")
    public Response getOrder(@PathParam("orderId") @NotNull @Positive Long orderId) {
        return Response.ok(tresenService.getOrderStatus(orderId)).build();
    }

    @POST
    @Path("/orders/{orderId}/pickup")
    @Consumes(MediaType.WILDCARD)
    public Response pickupOrder(@PathParam("orderId") @NotNull @Positive Long orderId) {
        tresenService.markOrderPickedUp(orderId);
        return Response.ok(tresenService.getOrderStatus(orderId)).build();
    }
}
