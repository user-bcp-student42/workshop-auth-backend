package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

@ApplicationScoped
public class AuthenticationService {
    @Inject
    UserRepository userRepository;

    @Inject
    TokenIssuer tokenIssuer;

    @Inject
    PasswordHasher passwordHasher;

    public Optional<AuthenticatedSession> authenticate(String username, String password) {
        UserCredential credential = userRepository.findByUsername(username);
        if (credential == null || !passwordHasher.verify(
            password,
            credential.passwordHash(),
            credential.passwordFormat()
        )) {
            return Optional.empty();
        }

        UserRecord user = new UserRecord(
                credential.id(),
                credential.username(),
                credential.displayName(),
                credential.role()
        );
        IssuedToken issuedToken = tokenIssuer.issueFor(user);
        return Optional.of(new AuthenticatedSession(user, issuedToken.value(), issuedToken.expiresIn()));
    }
}
