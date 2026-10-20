package com.bcp.workshop.auth;

public record AuthenticatedSession(UserRecord user, String token, long expiresIn) {}
