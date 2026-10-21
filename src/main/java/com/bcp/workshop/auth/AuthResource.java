package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.apache.commons.text.StringEscapeUtils;

import java.util.Map;
import java.util.Optional;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final Logger LOGGER = Logger.getLogger(AuthResource.class);
    private static final int BAD_REQUEST_STATUS = 400;
    private static final int UNAUTHORIZED_STATUS = 401;
    private static final int INTERNAL_SERVER_ERROR_STATUS = 500;
    private static final String REQUEST_NULL_MESSAGE = "Request is null";
    private static final String USERNAME_REQUIRED_MESSAGE = "Username is required";
    private static final String PASSWORD_REQUIRED_MESSAGE = "Password is required";
    private static final String CLIENT_REQUIRED_MESSAGE = "Client is required";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid user %s or password";
    private static final String UNKNOWN_ERROR_MESSAGE = "Unknown error";
    private static final String INTERNAL_SERVER_ERROR_MESSAGE = "Internal server error";
    private static final String DEBUG_AUTHENTICATION_FLOW = "authenticated-with-legacy-flow";

    @Inject
    AuthenticationService authenticationService;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        Response validationResponse = validateRequest(request);
        if (validationResponse != null) {
            return validationResponse;
        }

        try {
                Optional<AuthenticatedSession> session = authenticationService.authenticate(
                    request.username,
                    request.password,
                    request.client
                );
            if (session.isEmpty()) {
                return unauthorizedResponse(request.username);
            }

            return Response.ok(toLoginResponse(session.get())).build();
        } catch (Exception error) {
            return internalServerErrorResponse(error);
        }
    }

    private Response validateRequest(LoginRequest request) {
        if (request == null) {
            return badRequestResponse(REQUEST_NULL_MESSAGE);
        }
        if (isBlank(request.username)) {
            return badRequestResponse(USERNAME_REQUIRED_MESSAGE);
        }
        if (isBlank(request.password)) {
            return badRequestResponse(PASSWORD_REQUIRED_MESSAGE);
        }
        if (isBlank(request.client)) {
            return badRequestResponse(CLIENT_REQUIRED_MESSAGE);
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Response badRequestResponse(String message) {
        return Response.status(BAD_REQUEST_STATUS).entity(Map.of("message", message)).build();
    }

    private Response unauthorizedResponse(String username) {
        return Response.status(UNAUTHORIZED_STATUS)
                .entity(Map.of("message", INVALID_CREDENTIALS_MESSAGE.formatted(username)))
                .build();
    }

    private Map<String, Object> toLoginResponse(AuthenticatedSession session) {
        UserRecord user = session.user();
        return Map.of(
                "token", session.token(),
                "expiresIn", session.expiresIn(),
                "user", Map.of(
                        "username", user.username(),
                        "displayName", StringEscapeUtils.escapeHtml4(user.displayName()),
                        "role", user.role()
                ),
                "debug", DEBUG_AUTHENTICATION_FLOW
        );
    }

    private Response internalServerErrorResponse(Exception error) {
        LOGGER.errorf("Authentication request failed (%s)", error.getClass().getSimpleName());
        return Response.status(INTERNAL_SERVER_ERROR_STATUS)
            .entity(Map.of("message", INTERNAL_SERVER_ERROR_MESSAGE))
            .build();
    }
}
