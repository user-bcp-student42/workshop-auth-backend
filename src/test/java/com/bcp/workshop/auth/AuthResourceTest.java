package com.bcp.workshop.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuthResourceTest {
    private static final String LOGIN_PATH = "/auth/login";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String STUDENT_USERNAME = "student";
    private static final String STUDENT_PASSWORD = "Workshop123!";
    private static final int INTERNAL_SERVER_ERROR_STATUS = 500;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void logsInWithWorkshopUser() throws Exception {
        String token = given()
                .contentType(JSON_CONTENT_TYPE)
                .body("""
                        {"username":"student","password":"Workshop123!","client":"angular-workshop"}
                        """)
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(200)
                .body("token", instanceOf(String.class))
                .body("expiresIn", is(3600))
                .body("user.username", is(STUDENT_USERNAME))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("debug", is("authenticated-with-legacy-flow"))
                .extract()
                .path("token");

        JsonNode claims = decodeJwtClaims(token);
        assertEquals("angular-workshop", claims.path("client").asText());
        assertEquals("https://workshop-auth.test", claims.path("iss").asText());
        assertEquals(STUDENT_USERNAME, claims.path("sub").asText());
        assertEquals("STUDENT", claims.path("role").asText());
        assertTrue(claims.path("iat").isNumber());
        assertTrue(claims.path("exp").isNumber());
        assertEquals(3600L, claims.path("exp").asLong() - claims.path("iat").asLong());
    }

            @Test
            void logsInWithTrainerWithoutChangingResponseContract() {
            given()
                .contentType(JSON_CONTENT_TYPE)
                .body("""
                    {"username":"trainer","password":"Trainer123!","client":"angular-workshop"}
                    """)
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("expiresIn", is(3600))
                .body("user.username", is("trainer"))
                .body("user.displayName", is("Workshop Trainer"))
                .body("user.role", is("TRAINER"))
                .body("debug", is("authenticated-with-legacy-flow"));
            }

    @Test
    void rejectsMissingClient() {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body("""
                        {"username":"student","password":"Workshop123!"}
                        """)
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Client is required"));
    }

    @Test
    void rejectsNullClient() {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body("""
                        {"username":"student","password":"Workshop123!","client":null}
                        """)
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Client is required"));
    }

    @Test
    void rejectsEmptyClient() {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body("""
                        {"username":"student","password":"Workshop123!","client":""}
                        """)
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Client is required"));
    }

    @Test
    void rejectsWhitespaceOnlyClient() {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body("""
                        {"username":"student","password":"Workshop123!","client":"   "}
                        """)
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Client is required"));
    }

    @Test
    void rejectsMissingClientBeforeAuthentication() {
        AuthResource resource = new AuthResource();
        resource.authenticationService = failingAuthenticationService(
                new IllegalStateException("authentication must not run"));
        LoginRequest request = new LoginRequest();
        request.username = STUDENT_USERNAME;
        request.password = STUDENT_PASSWORD;

        Response response = resource.login(request);

        assertEquals(400, response.getStatus());
        assertEquals(Map.of("message", "Client is required"), response.getEntity());
    }

            @Test
            void rejectsIncorrectPasswordForKnownUser() {
            given()
                .contentType(JSON_CONTENT_TYPE)
                .body("""
                    {"username":"trainer","password":"incorrect","client":"angular-workshop"}
                    """)
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(401)
                .body("message", is("Invalid user trainer or password"));
            }

            @Test
            void allowsLocalFrontendOriginWithoutChangingLoginResponse() {
            given()
                .header("Origin", "http://localhost:4200")
                .contentType(JSON_CONTENT_TYPE)
                .body("""
                    {"username":"student","password":"Workshop123!","client":"angular-workshop"}
                    """)
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", is("http://localhost:4200"))
                .body("token", notNullValue())
                .body("expiresIn", is(3600))
                .body("user.username", is(STUDENT_USERNAME))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("debug", is("authenticated-with-legacy-flow"));
            }

            @Test
            void doesNotAllowUnconfiguredFrontendOrigin() {
            given()
                .header("Origin", "https://untrusted.example")
                .contentType(JSON_CONTENT_TYPE)
                .body("""
                    {"username":"student","password":"Workshop123!","client":"angular-workshop"}
                    """)
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(403)
                .header("Access-Control-Allow-Origin", nullValue());
            }

            @Test
            void rejectsUsernameWithLeadingWhitespaceInsteadOfTrimmingIt() {
            given()
                .contentType(JSON_CONTENT_TYPE)
                .body("""
                    {"username":" student","password":"Workshop123!","client":"angular-workshop"}
                    """)
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(401);
            }

            @Test
            void rejectsMalformedJson() {
            given()
                .contentType(JSON_CONTENT_TYPE)
                .body("{broken-json")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400);
            }

            @Test
            void rejectsUnsupportedContentType() {
            given()
                .contentType("text/plain")
                .body("username=student&password=Workshop123!")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(415);
            }

    @Test
    void rejectsNullRequest() {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body("null")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Request is null"));
    }

    @Test
    void rejectsMissingUsername() {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body("{\"password\":\"" + STUDENT_PASSWORD + "\",\"client\":\"angular-workshop\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsBlankUsername() {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body("{\"username\":\"  \",\"password\":\"" + STUDENT_PASSWORD + "\",\"client\":\"angular-workshop\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsMissingPassword() {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body("{\"username\":\"" + STUDENT_USERNAME + "\",\"client\":\"angular-workshop\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsBlankPassword() {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body("{\"username\":\"" + STUDENT_USERNAME + "\",\"password\":\"  \",\"client\":\"angular-workshop\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsInvalidCredentialsWithUsernameInMessage() {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body("{\"username\":\"unknown-user\",\"password\":\"incorrect\",\"client\":\"angular-workshop\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(401)
                .body("message", containsString("Invalid user unknown-user or password"));
    }

    @Test
    void doesNotLogSensitiveValuesAfterSuccessfulAuthentication() {
        String passwordSentinel = "sentinel-password-value";
        String tokenSentinel = "sentinel-token-value";
        String signingMaterialSentinel = "sentinel-signing-material";
        AuthResource resource = new AuthResource();
        resource.authenticationService = new AuthenticationService() {
            @Override
            public Optional<AuthenticatedSession> authenticate(String username, String password, String client) {
                UserRecord user = new UserRecord(1, username, "Workshop Student", "STUDENT");
                return Optional.of(new AuthenticatedSession(user, tokenSentinel, 3600));
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = STUDENT_USERNAME;
        request.password = passwordSentinel;
        request.client = "angular-workshop";

        try (TestLogCapture logs = new TestLogCapture(AuthResource.class)) {
            Response response = resource.login(request);
            assertEquals(200, response.getStatus());
            assertFalse(logs.output().contains(passwordSentinel));
            assertFalse(logs.output().contains(tokenSentinel));
            assertFalse(logs.output().contains(signingMaterialSentinel));
        }
    }

    @Test
    void returnsInternalServerErrorWithoutLoggingSensitiveValues() {
        String passwordSentinel = "sentinel-password-value";
        String tokenSentinel = "sentinel-token-value";
        String signingMaterialSentinel = "sentinel-signing-material";
        RuntimeException failure = new IllegalStateException(
                passwordSentinel + " " + tokenSentinel + " " + signingMaterialSentinel);
        AuthResource resource = new AuthResource();
        resource.authenticationService = failingAuthenticationService(failure);
        LoginRequest request = new LoginRequest();
        request.username = STUDENT_USERNAME;
        request.password = passwordSentinel;
        request.client = "angular-workshop";

        try (TestLogCapture logs = new TestLogCapture(AuthResource.class)) {
            Response response = resource.login(request);
            assertEquals(INTERNAL_SERVER_ERROR_STATUS, response.getStatus());
            assertEquals(Map.of("message", "Internal server error"), response.getEntity());
            String output = logs.output();
            assertTrue(output.contains("Authentication request failed (IllegalStateException)"));
            assertFalse(output.contains(passwordSentinel));
            assertFalse(output.contains(tokenSentinel));
            assertFalse(output.contains(signingMaterialSentinel));
        }
    }

    @Test
    void returnsFallbackMessageWhenAuthenticationErrorHasNoMessage() {
        AuthResource resource = new AuthResource();
        resource.authenticationService = failingAuthenticationService(new IllegalStateException());
        LoginRequest request = new LoginRequest();
        request.username = STUDENT_USERNAME;
        request.password = STUDENT_PASSWORD;
        request.client = "angular-workshop";

        Response response = resource.login(request);

        assertEquals(INTERNAL_SERVER_ERROR_STATUS, response.getStatus());
        assertEquals(Map.of("message", "Internal server error"), response.getEntity());
    }

    @Test
    void hidesInternalDetailsFromServerErrorResponse() {
        AuthResource resource = new AuthResource();
        resource.authenticationService = failingAuthenticationService(new IllegalStateException("database unavailable"));
        LoginRequest request = new LoginRequest();
        request.username = STUDENT_USERNAME;
        request.password = STUDENT_PASSWORD;
        request.client = "angular-workshop";

        Response response = resource.login(request);

        assertEquals(INTERNAL_SERVER_ERROR_STATUS, response.getStatus());
        assertEquals(Map.of("message", "Internal server error"), response.getEntity());
    }

    @Test
    void rejectsCredentialMarkedForPasswordReset() {
        AuthenticationService service = new AuthenticationService();
        service.userRepository = username -> new UserCredential(
                1,
                username,
                "",
                "RESET_REQUIRED",
                "Legacy User",
                "STUDENT"
        );
        service.passwordHasher = new PasswordHasher();

        AuthResource resource = new AuthResource();
        resource.authenticationService = service;
        LoginRequest request = new LoginRequest();
        request.username = "ambiguous-user";
        request.password = "provided-password";
        request.client = "angular-workshop";

        Response response = resource.login(request);

        assertEquals(401, response.getStatus());
        assertEquals(Map.of("message", "Invalid user ambiguous-user or password"), response.getEntity());
    }

    private AuthenticationService failingAuthenticationService(RuntimeException failure) {
        return new AuthenticationService() {
            @Override
            public Optional<AuthenticatedSession> authenticate(String username, String password, String client) {
                throw failure;
            }
        };
    }

    private JsonNode decodeJwtClaims(String compactToken) throws Exception {
        String[] segments = compactToken.split("\\.");
        assertEquals(3, segments.length);
        return objectMapper.readTree(Base64.getUrlDecoder().decode(segments[1]));
    }
}