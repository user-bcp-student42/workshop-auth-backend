package com.bcp.workshop.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenIssuerTest {
    private static final String TEST_ISSUER = "https://workshop-auth.test";
    private static final String TEST_CLIENT = "angular-workshop";
    private static final long TOKEN_LIFESPAN_SECONDS = 3600;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void issuesSignedJwtWithExplicitClaimsAndExpectedLifetime() throws Exception {
        Instant testStartedAt = Instant.now();
        IssuedToken issuedToken = newTokenIssuer().issueFor(
            new UserRecord(1, "student", "Workshop Student", "STUDENT"),
            TEST_CLIENT
        );
        Instant testFinishedAt = Instant.now();
        String[] jwtSegments = issuedToken.value().split("\\.");

        assertEquals(3, jwtSegments.length);
        JsonNode header = decodeJsonSegment(jwtSegments[0]);
        JsonNode claims = decodeJsonSegment(jwtSegments[1]);
        assertEquals("RS256", header.path("alg").asText());
        assertEquals("student", claims.path("sub").asText());
        assertEquals("STUDENT", claims.path("role").asText());
        assertEquals(TEST_CLIENT, claims.path("client").asText());
        assertEquals(TEST_ISSUER, claims.path("iss").asText());
        assertEquals(TOKEN_LIFESPAN_SECONDS, issuedToken.expiresIn());
        assertTrue(claims.path("iat").asLong() >= testStartedAt.getEpochSecond() - 1);
        assertTrue(claims.path("iat").asLong() <= testFinishedAt.getEpochSecond() + 1);
        assertTrue(claims.path("exp").asLong() > testStartedAt.getEpochSecond());
        assertEquals(TOKEN_LIFESPAN_SECONDS, claims.path("exp").asLong() - claims.path("iat").asLong());
        assertTrue(verifySignature(jwtSegments, loadPublicKey()));
    }

    @Test
    void rejectsJwtSignatureWhenVerifiedWithDifferentKey() throws Exception {
        IssuedToken issuedToken = newTokenIssuer().issueFor(
            new UserRecord(1, "student", "Workshop Student", "STUDENT"),
            TEST_CLIENT
        );
        String[] jwtSegments = issuedToken.value().split("\\.");
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);

        assertFalse(verifySignature(jwtSegments, keyPairGenerator.generateKeyPair().getPublic()));
    }

    @Test
    void productionSigningRequiresExternalKeyAndCannotIssueWithoutIt() throws IOException {
        Properties signingProperties = new Properties();
        try (InputStream propertiesFile = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            signingProperties.load(propertiesFile);
        }

        assertEquals("${AUTH_TOKEN_PRIVATE_KEY}", signingProperties.getProperty("smallrye.jwt.sign.key.location"));
        assertNull(signingProperties.getProperty("auth.token.secret"));
        assertThrows(
                IllegalStateException.class,
                () -> new TokenIssuer(TEST_ISSUER, "missing-private-key-for-production-test.pem")
        );
    }

    private TokenIssuer newTokenIssuer() {
        return new TokenIssuer(TEST_ISSUER, "privateKey.pem");
    }

    private JsonNode decodeJsonSegment(String segment) throws Exception {
        return objectMapper.readTree(Base64.getUrlDecoder().decode(segment));
    }

    private PublicKey loadPublicKey() throws Exception {
        try (InputStream keyResource = getClass().getResourceAsStream("/publicKey.pem")) {
            String pem = new String(keyResource.readAllBytes(), StandardCharsets.US_ASCII);
            String encodedKey = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        }
    }

    private boolean verifySignature(String[] jwtSegments, PublicKey publicKey) throws Exception {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update((jwtSegments[0] + "." + jwtSegments[1]).getBytes(StandardCharsets.US_ASCII));
        return verifier.verify(Base64.getUrlDecoder().decode(jwtSegments[2]));
    }
}