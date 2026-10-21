# Tasks

GitHub Issue [#12](https://github.com/user-bcp-student42/workshop-auth-backend/issues/12) remains the product work item; this checklist tracks implementation of the approved OpenSpec change only.

## 1. Characterization Baseline

- [x] 1.1 Run `mvn test -Dtest=AuthResourceTest,TokenIssuerTest` and record the passing baseline for the clientless trainer login, existing response fields/errors, RS256 signature, claims, and 3600-second lifetime.
- [x] 1.2 Add reusable log capture with sentinel exception data and verify the current error path emits raw throwable details; do not change logging in this task.

## 2. Login Client Contract

- [x] 2.1 Add focused `AuthResourceTest` cases for missing, null, empty, and whitespace-only `client` returning 400; verify the new cases fail against current behavior before implementation.
- [x] 2.2 Validate non-blank `client` before credential lookup and preserve it unchanged; verify all client validation cases pass and existing username/password and invalid-credential tests remain green.
- [x] 2.3 Update `docs/ENDPOINTS.md` to mark `client` required and document the 400 response; verify the documented request and error behavior matches `AuthResourceTest`.

## 3. Token Claims and Lifetime

- [x] 3.1 Pass the validated client through successful authentication to token issuance and include it in the signed claims; verify the decoded token contains the exact request value and the signature remains RS256-valid.
- [x] 3.2 Extend token and endpoint tests to verify `iss`, `sub`, `role`, `iat`, `exp`, `client`, unchanged response fields, `expiresIn` of 3600, and `exp - iat` agreement within one second; verify `AuthResourceTest` and `TokenIssuerTest` pass.
- [x] 3.3 Update `docs/ENDPOINTS.md` with the additive `client` claim and unchanged success response shape; verify the documented claim set matches the token tests.

## 4. Signing Configuration

- [x] 4.1 Remove the unused `auth.token.secret` fallback and add a production-profile check that missing external signing material cannot issue a token; verify signing continues to use the externally supplied `AUTH_TOKEN_PRIVATE_KEY` and no default signing value is used.
- [x] 4.2 Update the endpoint security documentation to state that production signing material is externally managed and has no default; verify the documented configuration matches `application.properties` and the production-profile check.

## 5. Sensitive Log Redaction

- [x] 5.1 Convert the characterization test to assert sentinel passwords, tokens, and signing material are absent from success and failure logs; restrict authentication error logging to safe metadata and verify the log-capture test passes without changing the generic 500 response.

## 6. Integration Verification

- [x] 6.1 Run `mvn test` and `openspec validate strengthen-auth-token-claims --strict`; verify the complete repository test suite and all approved OpenSpec artifacts pass validation.