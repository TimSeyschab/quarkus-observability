package de.shellnuts.api;

import de.shellnuts.application.OrderValidationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class OrderValidationExceptionMapper implements ExceptionMapper<OrderValidationException> {

    private static final Logger LOG = Logger.getLogger(OrderValidationExceptionMapper.class);

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(OrderValidationException exception) {
        LOG.warnf("event=order_validation_failed reason=\"%s\"", exception.getMessage());
        return ProblemDetailsFactory.badRequest("Bad Request", exception.getMessage(), uriInfo, null);
    }
}
