# Design

## Context

See [proposal.md](proposal.md) for motivation and [specs/auth-token/spec.md](specs/auth-token/spec.md) for required behavior.

`POST /auth/login` currently validates `username` and `password` in `AuthResource`, calls `AuthenticationService.authenticate(username, password)`, and returns the resulting token and session data without adding claims to the response. `LoginRequest` already has a `client` field, but it is not validated or forwarded. `TokenIssuer` emits an RS256 JWT with `iss`, `sub`, `role`, `iat`, and `exp`; the 3600-second lifetime is fixed, and the signing key is supplied through `AUTH_TOKEN_PRIVATE_KEY`. The documented success response also includes `user` and `debug`.

`application.properties` contains an `AUTH_TOKEN_SECRET` fallback property, but no executable source references it. The endpoint documentation and current issuer instead identify the external RSA private key as the signing input. The resource logs authentication exceptions; it does not intentionally log request fields or tokens, but an exception message or stack trace could contain sensitive data.

## Goals / Non-Goals

**Goals:**
- Carry a validated request client through authentication to token issuance without changing the response shape.
- Keep the existing signed-token contract and 3600-second lifetime while making claim and expiry guarantees verifiable.
- Ensure production never uses a default signing value and authentication logs cannot expose credentials, tokens, or signing material.
- Preserve existing login behavior except for the newly required `client` field.

**Non-Goals:**
- Select a different JWT library or signing algorithm, or introduce token validation, refresh, or revocation.
- Add client allowlists, normalization rules, persistence, or a new response field.
- Change credential verification, CORS, health checks, or error behavior unrelated to `client`.

## Protected Contracts

- Preserve the documented RS256 token with `iss`, `sub`, `role`, `iat`, and `exp`; add only the `client` claim.
- Preserve `expiresIn: 3600`, and make it agree with `exp - iat` within the specified one-second tolerance.
- Preserve successful response fields `token`, `expiresIn`, `user`, and `debug`, plus existing username/password, invalid-credential, CORS, and media-type behavior.
- The intentional compatibility change is that missing or blank `client` becomes `400 Bad Request`; clients must send a non-blank value.
- Do not implement the Issue's HMAC wording as an algorithm migration: it conflicts with the Day 1 contract and the current RSA implementation, while the Issue leaves the algorithm decision open.

## Components Affected

- `LoginRequest` and `AuthResource`: validate `client` at the HTTP boundary before credential lookup, then forward the original non-blank value.
- `AuthenticationService`: carry `client` only through the successful authentication path to token issuance.
- `TokenIssuer`: include the request client in the signed claims while retaining the current issuer, account claims, signing key, and lifetime contract.
- `application.properties`: remove the unused `auth.token.secret` fallback rather than wiring a second signing mechanism; retain the externally supplied `AUTH_TOKEN_PRIVATE_KEY` configuration and ensure production does not fall back to sandbox material.
- `AuthResourceTest` and `TokenIssuerTest`: characterize and verify request, response, claim, signature, expiry, and logging behavior.
- `docs/ENDPOINTS.md`: document the required `client`, its validation response, and the additive token claim after behavior is implemented.

## Decisions

1. **Validate `client` before authentication.** Keep HTTP request validation in `AuthResource` so invalid requests fail with `400` before database access or password verification. Validation rejects null, empty, and whitespace-only values. A non-blank value is forwarded unchanged; trimming or canonicalizing it would add an unstated client identity rule. Validating later in the service was considered, but would mix transport validation with credential logic and could perform unnecessary authentication work.

2. **Pass `client` as request context to token issuance.** Extend the existing authentication-to-issuer path with the validated client and add it to the JWT claims. Do not store it on the user record/session or add it to the HTTP response: it describes the login request, not the account, and the response contract is protected. Generating the claim in the resource was rejected because signing and claim construction belong at the token issuance boundary.

3. **Keep the current RSA signing contract and key source.** Continue using the documented RS256 contract and externally supplied `AUTH_TOKEN_PRIVATE_KEY`; do not migrate to HMAC or add a JWT dependency. An HMAC implementation following the Issue's contextual text was considered but rejected because it contradicts the baseline and would be a separate breaking security change. The unused `auth.token.secret` fallback should be removed rather than treated as a signing input; wiring it into signing would create a second, conflicting key source.

4. **Use one 3600-second lifetime and verify it against the claims.** Keep the established lifetime and derive the asserted relationship from `exp - iat`, which represents the issuance interval without depending on when a test decodes the token. A rolling calculation based on response time was considered but would make the contract less deterministic and could cause drift between token and response.

5. **Log safe failure metadata, not arbitrary exception content.** Preserve the generic `500` response. Replace raw exception-message/stack logging on the authentication path with a fixed event and non-sensitive exception type; do not log request bodies, passwords, tokens, or key material. Retaining unrestricted stack traces was considered, but throwable messages are not guaranteed to be free of secrets. This trades some immediate diagnostic detail for a defensible redaction guarantee.

## Incremental Strategy

1. Run the current focused tests and characterize the documented response and token contract before changing validation or issuance.
2. Add/update contract tests for the new `client` rejection and claim while retaining assertions for RS256, existing claims, response fields, and the 3600-second lifetime.
3. Pass `client` through the successful authentication path and emit it in the token; keep response mapping unchanged.
4. Remove the unused secret fallback, verify production key configuration has no default path, and add log-redaction coverage before changing exception logging.
5. Update `docs/ENDPOINTS.md` to match the verified behavior, then run the focused auth/token tests and the repository's full test suite.

## Characterization Before Critical Behavior Changes

Before changing request validation or token issuance, establish a passing baseline with `AuthResourceTest` and `TokenIssuerTest`:

- Pin successful login's status, response fields, `expiresIn`, `user`, and `debug`; pin existing `400`, `401`, `403`, and `415` behavior that is outside the new client rule.
- Record the current clientless trainer request as an existing success, then replace that expectation with the intentional `400` contract when enforcement is implemented. This makes the compatibility change explicit rather than silently altering a test fixture.
- Verify the JWT header is `RS256`, the existing claims and signature are valid with the test public key, and `exp - iat` is 3600 seconds. Add the `client` assertion without weakening these checks.
- Capture authentication log output with sentinel password, token, and key values for successful and failed/error paths. Confirm what the current exception logging exposes before replacing it, then assert the sentinels are absent.
- Verify configuration behavior with the production profile and external private-key setting; ensure the unused `AUTH_TOKEN_SECRET` fallback is not consumed and that missing production signing material cannot result in a token signed with a default.

## Risks / Trade-offs

- **Existing clients omit `client` -> they will receive 400 after enforcement.** Have clients send a non-blank value before the backend rollout; deploy them without depending on the claim until the backend emits it.
- **Removing raw stack traces reduces diagnostic detail ->** retain a fixed event identifier and exception type, and use controlled, redacted diagnostics if more detail is later required.
- **Issue text and the endpoint baseline name different algorithms ->** preserve RS256 and explicitly defer any algorithm migration to a separately scoped change.
- **An unused secret property may have been supplied by deployment configuration ->** verify repository and deployment references before removing it; never substitute it for the documented private key.
- **JWT times have epoch-second precision ->** test `exp - iat` against `expiresIn` using the documented one-second tolerance rather than wall-clock timing at response receipt.

## Migration Plan and Rollback

Before enforcing the new request requirement, update callers to send a non-blank `client`; the current backend accepts that extra request field, so this can be deployed first. Do not make callers depend on the token claim until the backend release that emits it is deployed. Then deploy the backend validation/claim change and verify login success, the new missing-client `400`, token signature/claims, expiry, and log redaction.

If the new requirement causes authentication failures, roll back the backend release to restore the prior optional-client contract and notify consumers not to rely on the additive claim until rollout resumes. Keep `AUTH_TOKEN_PRIVATE_KEY` externally managed during rollback; do not restore or enable the unused default-secret fallback. Roll back the request validation, claim addition, configuration cleanup, and corresponding documentation together so the deployed contract remains coherent.