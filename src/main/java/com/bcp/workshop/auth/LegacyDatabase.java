package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class LegacyDatabase implements UserRepository {
    private static final String CREATE_USERS_TABLE = "CREATE TABLE IF NOT EXISTS legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL, password_format TEXT NOT NULL DEFAULT 'LEGACY_PLAINTEXT')";
    private static final String SELECT_CREDENTIALS = "SELECT id, password, password_format FROM legacy_users";
    private static final String UPDATE_PASSWORD = "UPDATE legacy_users SET password = ?, password_format = ? WHERE id = ?";
    private static final String INSERT_USER = "INSERT OR IGNORE INTO legacy_users(username,password,display_name,role,password_format) VALUES (?,?,?,?,?)";
    private static final String FIND_USER = "SELECT id, username, password, password_format, display_name, role FROM legacy_users WHERE username = ?";
    private static final String ADD_PASSWORD_FORMAT = "ALTER TABLE legacy_users ADD COLUMN password_format TEXT NOT NULL DEFAULT 'LEGACY_PLAINTEXT'";

    private final String databaseUrl;
    private final PasswordHasher passwordHasher;
    private final boolean seedDefaultUsers;

    @Inject
    public LegacyDatabase(
            @ConfigProperty(name = "auth.db.url", defaultValue = "jdbc:sqlite:./data/workshop.db") String databaseUrl,
            PasswordHasher passwordHasher,
            @ConfigProperty(name = "auth.db.seed-users", defaultValue = "true") boolean seedDefaultUsers
    ) {
        this.databaseUrl = databaseUrl;
        this.passwordHasher = passwordHasher;
        this.seedDefaultUsers = seedDefaultUsers;
    }

    LegacyDatabase(String databaseUrl, PasswordHasher passwordHasher) {
        this(databaseUrl, passwordHasher, true);
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(Path.of("data"));
            try (Connection connection = DriverManager.getConnection(databaseUrl)) {
                connection.setAutoCommit(false);
                try {
                    createUsersTable(connection);
                    ensurePasswordFormatColumn(connection);
                    if (seedDefaultUsers) {
                        seedDefaultUsers(connection);
                    }
                    migrateLegacyPasswords(connection);
                    connection.commit();
                } catch (Exception error) {
                    connection.rollback();
                    throw error;
                }
            }
        } catch (Exception error) {
            error.printStackTrace();
        }
    }

    @Override
    public UserCredential findByUsername(String username) {
        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            return findByUsername(connection, username);
        } catch (Exception error) {
            error.printStackTrace();
        }
        return null;
    }

    private void createUsersTable(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_USERS_TABLE);
        }
    }

    private void seedDefaultUsers(Connection connection) throws Exception {
        insertDefaultUser(connection, "student", "Workshop123!", "Workshop Student", "STUDENT");
        insertDefaultUser(connection, "trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
    }

    private void insertDefaultUser(
            Connection connection,
            String username,
            String password,
            String displayName,
            String role
    ) throws Exception {
        if (findByUsername(connection, username) != null) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(INSERT_USER)) {
            statement.setString(1, username);
            statement.setString(2, passwordHasher.hash(password));
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.setString(5, PasswordHasher.BCRYPT_FORMAT);
            statement.executeUpdate();
        }
    }

    private void ensurePasswordFormatColumn(Connection connection) throws Exception {
        if (hasPasswordFormatColumn(connection)) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(ADD_PASSWORD_FORMAT);
        }
    }

    private boolean hasPasswordFormatColumn(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet columns = statement.executeQuery("PRAGMA table_info(legacy_users)")) {
            while (columns.next()) {
                if ("password_format".equals(columns.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void migrateLegacyPasswords(Connection connection) throws Exception {
        List<PasswordUpdate> passwordUpdates = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(SELECT_CREDENTIALS)) {
            while (result.next()) {
                String password = result.getString("password");
                String format = result.getString("password_format");
                if (PasswordHasher.BCRYPT_FORMAT.equals(format) || PasswordHasher.RESET_REQUIRED_FORMAT.equals(format)) {
                    continue;
                }
                if (!PasswordHasher.LEGACY_PLAINTEXT_FORMAT.equals(format)) {
                    throw new IllegalStateException("Unsupported stored password format");
                }

                if (passwordHasher.hasBcryptEncoding(password)) {
                    passwordUpdates.add(new PasswordUpdate(
                            result.getLong("id"),
                            "",
                            PasswordHasher.RESET_REQUIRED_FORMAT
                    ));
                } else {
                    passwordUpdates.add(new PasswordUpdate(
                            result.getLong("id"),
                            passwordHasher.hash(password),
                            PasswordHasher.BCRYPT_FORMAT
                    ));
                }
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(UPDATE_PASSWORD)) {
            for (PasswordUpdate update : passwordUpdates) {
                statement.setString(1, update.password());
                statement.setString(2, update.format());
                statement.setLong(3, update.id());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private UserCredential findByUsername(Connection connection, String username) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(FIND_USER)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new UserCredential(
                        result.getLong("id"),
                        result.getString("username"),
                        result.getString("password"),
                        result.getString("password_format"),
                        result.getString("display_name"),
                        result.getString("role")
                );
            }
        }
    }

    private record PasswordUpdate(long id, String password, String format) {}
}
