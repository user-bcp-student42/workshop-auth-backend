package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class HealthDownTestProfile implements QuarkusTestProfile {
    @Override
    public String getConfigProfile() {
        return "health-down";
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "auth.db.url", "jdbc:sqlite:target/health-down-test.db",
                "auth.token.issuer", "https://workshop-auth.test",
                "smallrye.jwt.sign.key.location", "privateKey.pem"
        );
    }
}