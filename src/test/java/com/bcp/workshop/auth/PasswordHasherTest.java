package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    private final PasswordHasher passwordHasher = new PasswordHasher();

    @Test
    void hashesPasswordsWithUniqueSaltsAndVerifiesThem() {
        String firstHash = passwordHasher.hash("Workshop123!");
        String secondHash = passwordHasher.hash("Workshop123!");

        assertNotEquals(firstHash, secondHash);
        assertTrue(passwordHasher.hasBcryptEncoding(firstHash));
        assertTrue(passwordHasher.verify("Workshop123!", firstHash, PasswordHasher.BCRYPT_FORMAT));
        assertFalse(passwordHasher.verify("incorrect", firstHash, PasswordHasher.BCRYPT_FORMAT));
        assertFalse(passwordHasher.verify("Workshop123!", firstHash, PasswordHasher.RESET_REQUIRED_FORMAT));
    }
}