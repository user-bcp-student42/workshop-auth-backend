# Proposal

## Why

Issue [#12](https://github.com/user-bcp-student42/workshop-auth-backend/issues/12) identifies missing guarantees around the login token's client claim, expiry consistency, and handling of signing secrets and sensitive logs. Making these guarantees explicit protects consumers from ambiguous token identity and lifetime while preserving the documented login response contract.

## What Changes

- Require a non-blank `client` in `POST /auth/login`; missing, null, empty, or whitespace-only values become a documented `400 Bad Request`. This intentionally changes the current request contract, where `client` is optional, because Issue #12 requires the validated request client to be present in every issued token.
- Add the validated request `client` as a token claim while retaining the existing documented claims `iss`, `sub`, `role`, `iat`, and `exp`.
- Specify observable expiry consistency between `expiresIn` and the token's `exp`, with a documented tolerance of one second for epoch-second precision.
- Require that signing material is not supplied by a production default and that tokens, credentials, and signing secrets are not exposed in logs.
- Preserve the successful response fields and the existing behavior for username/password validation, invalid credentials, CORS, and content negotiation.

## Scope

- The login request's `client` validation and its effect on token claims.
- Verifiable claims and expiry guarantees for successfully issued tokens.
- Production handling of signing material and redaction of sensitive values from logs.
- Documentation and tests needed to make these behaviors observable.

## Out of Scope

- Choosing or migrating the signing algorithm or JWT library.
- Token refresh, revocation, or validation of tokens presented to this backend.
- Changes to health endpoints, credential verification, CORS policy, or the successful response shape.
- A client allowlist or format policy beyond requiring a non-blank string.

## Behavior to Preserve

- `POST /auth/login` remains unauthenticated and consumes and produces JSON under the existing CORS policy.
- Username and password continue to be required, whitespace-only values continue to be rejected, and usernames are not trimmed before authentication.
- Successful login continues to return `token`, `expiresIn` (3600 seconds), `user`, and `debug` with their documented meanings and values.
- Invalid credentials continue to return `401`; existing request and server error behavior remains unchanged except for the newly required `client` validation.
- The documented signed-token contract remains RS256 with `iss`, `sub`, `role`, `iat`, and `exp`; `client` is additive. Issue #12's contextual reference to HMAC and `AUTH_TOKEN_SECRET` conflicts with the Day 1 endpoint contract and current implementation. Its algorithm/library choice is explicitly left open, so this proposal does not change that contract.

## Capabilities

### New Capabilities

- `auth-token`: Guarantees for claims, expiry, and safe handling of tokens issued by the login flow.

### Modified Capabilities

- None. The repository has no existing OpenSpec capabilities; this change introduces the first capability spec.

## Acceptance Criteria

- Requests with a missing or blank `client` receive `400`; a valid client is reflected in the signed token.
- A successful token contains `iss`, `sub`, `role`, `iat`, `exp`, and `client`, and its lifetime agrees with `expiresIn` within one second.
- The successful response shape, 3600-second duration, and existing login outcomes remain unchanged for requests that satisfy the new client requirement.
- Production does not rely on a default signing secret, and authentication logs do not expose tokens, credentials, or signing secrets.

## Impact

The externally visible impact is limited to the login request requirement and the additional token claim. The request/response contract documentation, authentication tests, token issuance behavior, production signing configuration, and logging behavior may be affected. No implementation approach is selected here.