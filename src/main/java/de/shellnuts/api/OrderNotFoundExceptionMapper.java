package de.shellnuts.api;

import de.shellnuts.application.OrderNotFoundException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class OrderNotFoundExceptionMapper implements ExceptionMapper<OrderNotFoundException> {

    private static final Logger LOG = Logger.getLogger(OrderNotFoundExceptionMapper.class);

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(OrderNotFoundException exception) {
        LOG.warnf("event=order_not_found reason=\"%s\"", exception.getMessage());
        return ProblemDetailsFactory.notFound(exception.getMessage(), uriInfo);
    }
}
