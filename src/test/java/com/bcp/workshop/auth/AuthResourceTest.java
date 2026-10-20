package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AuthResourceTest {
    private static final String LOGIN_PATH = "/auth/login";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String STUDENT_USERNAME = "student";
    private static final String STUDENT_PASSWORD = "Workshop123!";

    @Test
    void logsInWithWorkshopUser() {
        given()
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
                .body("debug", is("authenticated-with-legacy-flow"));
    }

            @Test
            void logsInWithTrainerWithoutChangingResponseContract() {
            given()
                .contentType(JSON_CONTENT_TYPE)
                .body("""
                    {"username":"trainer","password":"Trainer123!"}
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
            void rejectsIncorrectPasswordForKnownUser() {
            given()
                .contentType(JSON_CONTENT_TYPE)
                .body("""
                    {"username":"trainer","password":"incorrect"}
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
                    {"username":"student","password":"Workshop123!"}
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
                    {"username":"student","password":"Workshop123!"}
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
                    {"username":" student","password":"Workshop123!"}
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
                .body("{\"password\":\"" + STUDENT_PASSWORD + "\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsBlankUsername() {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body("{\"username\":\"  \",\"password\":\"" + STUDENT_PASSWORD + "\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsMissingPassword() {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body("{\"username\":\"" + STUDENT_USERNAME + "\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsBlankPassword() {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body("{\"username\":\"" + STUDENT_USERNAME + "\",\"password\":\"  \"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsInvalidCredentialsWithUsernameInMessage() {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body("{\"username\":\"unknown-user\",\"password\":\"incorrect\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(401)
                .body("message", containsString("Invalid user unknown-user or password"));
    }

    @Test
    void returnsInternalServerErrorWhenAuthenticationFails() {
        AuthResource resource = new AuthResource();
        resource.authenticationService = failingAuthenticationService(new IllegalStateException("database unavailable"));
        LoginRequest request = new LoginRequest();
        request.username = STUDENT_USERNAME;
        request.password = STUDENT_PASSWORD;

        Response response = resource.login(request);

        assertEquals(INTERNAL_SERVER_ERROR_STATUS, response.getStatus());
        assertEquals(Map.of("message", "Internal server error"), response.getEntity());
    }

    @Test
    void returnsFallbackMessageWhenAuthenticationErrorHasNoMessage() {
        AuthResource resource = new AuthResource();
        resource.authenticationService = failingAuthenticationService(new IllegalStateException());
        LoginRequest request = new LoginRequest();
        request.username = STUDENT_USERNAME;
        request.password = STUDENT_PASSWORD;

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

        Response response = resource.login(request);

        assertEquals(401, response.getStatus());
        assertEquals(Map.of("message", "Invalid user ambiguous-user or password"), response.getEntity());
    }

    private AuthenticationService failingAuthenticationService(RuntimeException failure) {
        return new AuthenticationService() {
            @Override
            public Optional<AuthenticatedSession> authenticate(String username, String password) {
                throw failure;
            }
        };
    }

    private static final int INTERNAL_SERVER_ERROR_STATUS = 500;
}
