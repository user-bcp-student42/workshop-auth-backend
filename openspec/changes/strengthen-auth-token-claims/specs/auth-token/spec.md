# Spec Delta

## Purpose

Defines the observable guarantees for tokens issued by the login flow, including their identity claims, lifetime, and protection of signing material and sensitive values.

## ADDED Requirements

### Requirement: Login requires a non-blank client

The login endpoint MUST require `client` to be a non-blank string before authenticating a request.

#### Scenario: Login request omits or provides a blank client
- **WHEN** a request omits `client` or provides it as null, empty, or whitespace-only
- **THEN** the endpoint responds with `400 Bad Request` and a message identifying that `client` is required

#### Scenario: Login request provides a valid client
- **WHEN** a request provides a non-blank `client` and valid credentials
- **THEN** login succeeds and the issued token contains that client value

### Requirement: Issued tokens preserve documented claims and include client

Every successfully issued login token MUST be signed with the documented RS256 contract and contain `iss`, `sub`, `role`, `iat`, `exp`, and `client` claims.

#### Scenario: Successful login issues the complete claim set
- **WHEN** a login request with valid credentials and a non-blank client succeeds
- **THEN** the signed token contains `iss`, `sub`, `role`, `iat`, `exp`, and `client`
- **AND** `sub` and `role` identify the authenticated account
- **AND** `client` equals the validated value from the request

#### Scenario: Adding the client claim preserves the login response
- **WHEN** a login request with valid credentials and a non-blank client succeeds
- **THEN** the response retains `token`, `expiresIn`, `user`, and `debug` with their documented meanings
- **AND** the response does not require a new top-level field for `client`

### Requirement: Token lifetime agrees with expiresIn

The token expiration and response `expiresIn` MUST describe the same lifetime within one second of epoch-second precision; the documented lifetime remains 3600 seconds.

#### Scenario: Successful token lifetime is consistent
- **WHEN** a successful login response and its token are inspected
- **THEN** `expiresIn` is 3600 seconds
- **AND** the difference between `exp` and `iat` agrees with `expiresIn` within one second

### Requirement: Production signing material is not defaulted

Production token issuance MUST NOT rely on a known, built-in, or sandbox-only default signing secret or key.

#### Scenario: Production has no externally supplied signing material
- **WHEN** production signing material is absent
- **THEN** the service MUST NOT issue tokens using a default or sandbox signing value

#### Scenario: Sandbox-only signing defaults are documented
- **WHEN** a default signing value is available in a non-production environment
- **THEN** its documentation identifies it as sandbox-only and unsuitable for deployment

### Requirement: Sensitive authentication values are not logged

Authentication logs MUST NOT expose issued tokens, submitted passwords, or signing secrets and keys.

#### Scenario: Successful login does not log sensitive values
- **WHEN** a login succeeds
- **THEN** its token, submitted password, and signing material do not appear in authentication logs

#### Scenario: Failed login does not log credentials or signing material
- **WHEN** authentication fails
- **THEN** the submitted password and signing material do not appear in authentication logs