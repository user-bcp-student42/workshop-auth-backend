package com.bcp.workshop.auth;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@ApplicationScoped
public class TokenIssuer {
    private static final long TOKEN_LIFESPAN_SECONDS = 3600;

    private final String issuer;
    private final PrivateKey signingKey;

    @Inject
    public TokenIssuer(
            @ConfigProperty(name = "auth.token.issuer") String issuer,
            @ConfigProperty(name = "smallrye.jwt.sign.key.location") String signingKeyLocation
    ) {
        this.issuer = issuer;
        this.signingKey = loadSigningKey(signingKeyLocation);
    }

    public IssuedToken issueFor(UserRecord user) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + TOKEN_LIFESPAN_SECONDS;
        String value = Jwt.claims(Map.of(
                "iss", issuer,
                "sub", user.username(),
                "role", user.role(),
                "iat", issuedAt,
                "exp", expiresAt
        )).jws().sign(signingKey);
        return new IssuedToken(value, TOKEN_LIFESPAN_SECONDS);
    }

    private static PrivateKey loadSigningKey(String location) {
        try {
            String keyPem;
            try (InputStream classpathKey = TokenIssuer.class.getClassLoader().getResourceAsStream(location)) {
                keyPem = classpathKey == null
                        ? Files.readString(Path.of(location))
                        : new String(classpathKey.readAllBytes(), StandardCharsets.US_ASCII);
            }

            String encodedKey = keyPem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to load JWT signing key", error);
        }
    }
}
