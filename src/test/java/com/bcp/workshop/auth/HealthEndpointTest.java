package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class HealthEndpointTest {
    private static final String HEALTH_PATH = "/q/health";

    @Test
    void reportsUpWhenNoHealthChecksAreRegistered() {
        given()
                .when().get(HEALTH_PATH)
                .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("checks", empty());
    }

    @Test
    void rejectsUnconfiguredBrowserOrigin() {
        given()
                .header("Origin", "https://untrusted.example")
                .when().get(HEALTH_PATH)
                .then()
                .statusCode(403)
                .header("Access-Control-Allow-Origin", nullValue());
    }
}