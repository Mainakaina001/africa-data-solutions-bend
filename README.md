# Africa Data Solutions — Spring Boot API

Java/Spring Boot port of the Node/Express `backend` service: a fintech-telecom
platform for wallet management, virtual-account funding (Billstack), data/airtime
delivery (SME Plug, VTPass), and bill payments (electricity/TV/education via VTPass).

## Stack

- Java 17, Spring Boot 4.1 (Spring Framework 7)
- Spring Data JPA (Hibernate) + Flyway migrations, PostgreSQL
- Spring Security (stateless JWT, HS256) + method security (`@PreAuthorize`)
- RestTemplate per external provider, with a shared `RetryTemplate`
  (exponential backoff) applied to idempotent (GET) calls only — purchase/pay
  POSTs are never retried, since retrying a non-idempotent money-moving call
  risks double-charging upstream
- Argon2id password/PIN hashing (Spring Security's `Argon2PasswordEncoder`)
- Hand-rolled RFC 6238 TOTP for 2FA + ZXing for QR enrollment codes
- Firebase Admin SDK for FCM push notifications (optional — disabled if unconfigured)
- `@Scheduled` tasks in-process for the outbox dispatcher and order reconciliation
  (replacing the separate `worker:outbox` / `worker:reconcile` Node processes)

## Configuration

All settings are environment variables, matching the same names used by the
original `backend/.env` file — you can reuse those values directly as OS
environment variables. See `src/main/resources/application.yml` for the full
list and defaults. At minimum, set:

```
JWT_SECRET                  (>= 32 chars, required)
BILLSTACK_API_KEY
BILLSTACK_SECRET_KEY
BILLSTACK_WEBHOOK_SECRET    (>= 32 chars, required)
SME_PLUG_API_KEY
FRONTEND_ORIGINS            (comma-separated allowlist, no wildcards)
DATABASE_URL                (jdbc:postgresql://host:5432/dbname)
DATABASE_USERNAME
DATABASE_PASSWORD
```

The app refuses to start if `JWT_SECRET` / `BILLSTACK_WEBHOOK_SECRET` are
missing or a known-weak value (same fail-fast policy as the Node version).

## Running

```
./mvnw spring-boot:run
```

Flyway migrations run automatically on startup (`src/main/resources/db/migration`)
against a PostgreSQL database — create it first (no auto-create). Swagger UI is
at `/api-docs`, the raw OpenAPI doc at `/api-docs.json`.

## Notable design decisions vs. the Node version

- **Wallet locking**: `WalletService` uses `EntityManager.find(..., LockModeType.PESSIMISTIC_WRITE)`
  directly (a `SELECT ... FOR UPDATE`) inside a `SERIALIZABLE` transaction —
  the same concurrency model as the original, implemented against the JPA
  `EntityManager` rather than Spring Data repository sugar.
- **Failure classification**: VTPass errors are now classified DEFINITIVE vs.
  AMBIGUOUS the same way SME Plug's already were. The original Node airtime
  controller treated *every* VTPass error as terminal (including network
  timeouts), which risked refunding a purchase VTPass had actually delivered.
  Bills/Data/Airtime now all share the same safe refund-vs-reconcile logic.
  See `FailureClassification` / `VtPassClient`.
- **Login/profile response**: no longer echoes `tokenVersion`,
  `failedLoginAttempts`, or `lockedUntil` to the client (the Node response
  did). See `AuthenticatedUserView`.
- **Rate limiting**: in-memory, per-IP (the Node version additionally keys the
  auth/OTP limiters by request-body email; that's dropped here to avoid
  buffering the request body ahead of validation — IP-based limiting still
  covers the dominant brute-force shape). No Redis-backed shared store, same
  as the Node fallback when `REDIS_URL` isn't set.
- **Password hashing**: Argon2id only — the Node bcrypt-legacy-rehash path
  isn't needed since there's no existing bcrypt data to migrate.
