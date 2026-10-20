package com.bcp.workshop.auth;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.regex.Pattern;

@ApplicationScoped
public class PasswordHasher {
    public static final String LEGACY_PLAINTEXT_FORMAT = "LEGACY_PLAINTEXT";
    public static final String BCRYPT_FORMAT = "BCRYPT_V1";
    public static final String RESET_REQUIRED_FORMAT = "RESET_REQUIRED";

    private static final Pattern BCRYPT_ENCODING_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    public String hash(String password) {
        return BcryptUtil.bcryptHash(password);
    }

    public boolean verify(String password, String passwordHash, String passwordFormat) {
        return BCRYPT_FORMAT.equals(passwordFormat) && BcryptUtil.matches(password, passwordHash);
    }

    public boolean hasBcryptEncoding(String value) {
        return value != null && BCRYPT_ENCODING_PATTERN.matcher(value).matches();
    }
}