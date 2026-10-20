package com.bcp.workshop.auth;

public interface UserRepository {
    UserCredential findByUsername(String username);
}
