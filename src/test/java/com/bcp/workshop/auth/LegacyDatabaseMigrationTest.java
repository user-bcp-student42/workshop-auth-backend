package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyDatabaseMigrationTest {
    private static final String LEGACY_PASSWORD = "Workshop123!";

    @TempDir
    Path temporaryDirectory;

    @Test
    void migratesPlaintextPasswordsAndIsIdempotent() throws Exception {
        String databaseUrl = "jdbc:sqlite:" + temporaryDirectory.resolve("legacy-users.db");
        createLegacyDatabase(databaseUrl);
        PasswordHasher passwordHasher = new PasswordHasher();
        LegacyDatabase database = new LegacyDatabase(databaseUrl, passwordHasher);

        database.initialize();

        String migratedHash = readPassword(databaseUrl, "student");
        assertNotEquals(LEGACY_PASSWORD, migratedHash);
        assertTrue(passwordHasher.hasBcryptEncoding(migratedHash));
        assertTrue(passwordHasher.verify(LEGACY_PASSWORD, migratedHash, PasswordHasher.BCRYPT_FORMAT));
        assertEquals("BCRYPT_V1", readPasswordFormat(databaseUrl, "student"));
        assertNotNull(database.findByUsername("student"));
        assertNotNull(database.findByUsername("trainer"));

        database.initialize();

        assertEquals(migratedHash, readPassword(databaseUrl, "student"));
        assertEquals("BCRYPT_V1", readPasswordFormat(databaseUrl, "student"));
    }

    @Test
    void requiresPasswordResetForBcryptShapedLegacyPlaintext() throws Exception {
        String databaseUrl = "jdbc:sqlite:" + temporaryDirectory.resolve("ambiguous-users.db");
        PasswordHasher passwordHasher = new PasswordHasher();
        String legacyPlaintextThatLooksLikeHash = passwordHasher.hash("different-original-password");
        createLegacyDatabase(databaseUrl, "ambiguous-user", legacyPlaintextThatLooksLikeHash);
        LegacyDatabase database = new LegacyDatabase(databaseUrl, passwordHasher);

        database.initialize();

        assertEquals("RESET_REQUIRED", readPasswordFormat(databaseUrl, "ambiguous-user"));
        assertEquals("", readPassword(databaseUrl, "ambiguous-user"));
    }

    @Test
    void doesNotSeedDefaultUsersWhenSeedingIsDisabled() throws Exception {
        String databaseUrl = "jdbc:sqlite:" + temporaryDirectory.resolve("empty-users.db");
        LegacyDatabase database = new LegacyDatabase(databaseUrl, new PasswordHasher(), false);

        database.initialize();

        assertNull(database.findByUsername("student"));
        assertNull(database.findByUsername("trainer"));
    }

    @Test
    void preservesExistingUsersWhenSeedingIsDisabled() throws Exception {
        String databaseUrl = "jdbc:sqlite:" + temporaryDirectory.resolve("existing-users.db");
        createLegacyDatabase(databaseUrl);
        LegacyDatabase database = new LegacyDatabase(databaseUrl, new PasswordHasher(), false);

        database.initialize();

        assertNotNull(database.findByUsername("student"));
        assertNull(database.findByUsername("trainer"));
    }

    private void createLegacyDatabase(String databaseUrl) throws Exception {
        createLegacyDatabase(databaseUrl, "student", LEGACY_PASSWORD);
    }

    private void createLegacyDatabase(String databaseUrl, String username, String password) throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
        }

        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)")) {
            statement.setString(1, username);
            statement.setString(2, password);
            statement.setString(3, "Legacy User");
            statement.setString(4, "STUDENT");
            statement.executeUpdate();
        }
    }

    private String readPassword(String databaseUrl, String username) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password FROM legacy_users WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString("password") : null;
            }
        }
    }

    private String readPasswordFormat(String databaseUrl, String username) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password_format FROM legacy_users WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString("password_format") : null;
            }
        }
    }
}