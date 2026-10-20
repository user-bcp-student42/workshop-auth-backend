package com.bcp.workshop.auth;

public record UserCredential(
	long id,
	String username,
	String passwordHash,
	String passwordFormat,
	String displayName,
	String role
) {}