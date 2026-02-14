package de.shellnuts.api;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.Map;

public final class ProblemDetailsFactory {

    private static final String CONTENT_TYPE = "application/problem+json";

    private ProblemDetailsFactory() {
    }

    public static Response badRequest(String title, String detail, UriInfo uriInfo, Map<String, String> violations) {
        return build(Response.Status.BAD_REQUEST, "https://httpstatuses.com/400", title, detail, uriInfo, violations);
    }

    public static Response notFound(String detail, UriInfo uriInfo) {
        return build(Response.Status.NOT_FOUND, "https://httpstatuses.com/404", "Not Found", detail, uriInfo, null);
    }

    private static Response build(Response.Status status, String type, String title, String detail,
                                  UriInfo uriInfo, Map<String, String> violations) {
        OrderApiModels.ProblemDetails problem = new OrderApiModels.ProblemDetails(
                type,
                title,
                status.getStatusCode(),
                detail,
                uriInfo != null ? uriInfo.getPath() : null,
                violations);
        return Response.status(status)
                .type(MediaType.valueOf(CONTENT_TYPE))
                .entity(problem)
                .build();
    }
}
