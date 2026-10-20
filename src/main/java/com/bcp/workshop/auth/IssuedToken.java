package com.bcp.workshop.auth;

public record IssuedToken(String value, long expiresIn) {}
