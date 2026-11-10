# Users service

Standalone Spring Boot 3 / Java 17 REST service for user accounts, password-based login and role-aware authorization. It persists to Oracle and issues signed, expiring JWT bearer tokens. No development or production credentials are embedded in the project.

## Requirements and run

- Docker, or Java 17 and Maven 3.9+.
- An Oracle service and an Oracle account with permission to create/alter objects in its own schema.
- Export configuration before starting (examples below use placeholders; replace them locally and do not commit secrets):

```sh
export ORACLE_JDBC_URL='jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
export ORACLE_USERNAME='USERS_SERVICE'
export ORACLE_PASSWORD='<oracle-account-password>'
export JWT_SECRET='<base64-encoded-random-secret-of-at-least-32-bytes>'
export JWT_EXPIRATION='PT1H'
```

Generate a signing secret with a trusted secret manager or local cryptographic tooling. The application rejects a missing, malformed, or less-than-256-bit `JWT_SECRET`. Configure a distinct high-entropy value per environment; never use the integration-test key in deployment.

Run locally with `mvn spring-boot:run`, or build and run in Docker:

```sh
docker build -t users-service:local .
docker run --rm -p 8081:8081 --env ORACLE_JDBC_URL --env ORACLE_USERNAME --env ORACLE_PASSWORD --env JWT_SECRET --env JWT_EXPIRATION users-service:local
```

`DDL_AUTO` defaults to `update` for a first local run. The application owns the `APP_USERS` table in the current schema of `ORACLE_USERNAME`; it does not create another Oracle schema. The service account is the table owner and must be the same account used by the forum-service integration seed SQL. For controlled deployments, set `DDL_AUTO=validate` after applying reviewed DDL. Do not run with schema-update privileges in a restricted production deployment.

## Persistence contract / seed ownership

Hibernate creates `APP_USERS` (unqualified, in the configured Oracle account's schema) with these columns and semantics:

| Column | Oracle type | Meaning |
| --- | --- | --- |
| `ID` | `NUMBER(19,0)` identity primary key | Generated user ID |
| `USERNAME` | `VARCHAR2(50)` unique, not null | Login name |
| `EMAIL` | `VARCHAR2(254)` unique, not null | Contact address |
| `PASSWORD_HASH` | `VARCHAR2(100)` not null | BCrypt hash only, never a cleartext password |
| `ROLE` | `VARCHAR2(10)` not null | Exactly `USER` or `ADMIN` |

The users service is the sole owner of this table. Other services should refer to user identity by the returned numeric `id` and must not write user rows directly. The parent may seed this table using the configured service schema owner; omit `ID` so Oracle generates it, and provide a BCrypt hash (never the original password) in `PASSWORD_HASH`. There is intentionally no default account or public sign-up endpoint: bootstrap the first administrator through controlled DBA/seed tooling, then manage accounts through the admin-only API. The service's user creation request only accepts an explicit role when authenticated as `ADMIN`.

## Authorization and inter-service identity contract

- `USER` can authenticate and call `GET /api/auth/me` for their own identity; they cannot call any `/api/users` account-management route.
- `ADMIN` can list, read, create, modify (including role), and delete accounts.
- Roles are loaded from Oracle for each authenticated request, not trusted from client input or a token role claim. A role change therefore affects subsequent requests after commit.
- All `/api/users` endpoints require `ADMIN`; no JWT means `401`, and an authenticated non-admin means `403`.
- Password input is BCrypt-encoded before persistence. Password hashes are omitted from all user responses. Login failures return `401` without differentiating unknown usernames from incorrect passwords.

Forum service contract: after login, pass the returned token unchanged in `Authorization: Bearer <token>` to `GET http://users-service:8081/api/auth/me` (use the configured service URL). An authorized request returns HTTP `200` JSON:

```json
{"id":1,"username":"member","role":"USER"}
```

The forum service must treat `401` or any non-`200` as unauthenticated, and authorize using the returned role only after successful validation. This endpoint verifies the token signature and expiry and resolves its subject against the current account. It is an HTTP validation contract; no shared signing key or token parsing is required by the forum service.

## REST API

JSON requests and responses; set `Content-Type: application/json` for request bodies.

| Method | Path | Access | Result |
| --- | --- | --- | --- |
| `POST` | `/api/auth/login` | Public | `200 {"token":"…","tokenType":"Bearer"}` |
| `GET` | `/api/auth/me` | Any authenticated role | `200 {"id":1,"username":"member","role":"USER"}` |
| `GET` | `/api/users` | `ADMIN` | `200` array of users |
| `GET` | `/api/users/{id}` | `ADMIN` | `200` user, `404` if absent |
| `POST` | `/api/users` | `ADMIN` | `201` user; input includes username, email, password (12–72 chars), role |
| `PUT` | `/api/users/{id}` | `ADMIN` | `200` user; input includes username, email, role; password is unchanged |
| `DELETE` | `/api/users/{id}` | `ADMIN` | `204`; `404` if absent |

Create request example:

```json
{"username":"member","email":"member@example.test","password":"Replace-this-123!","role":"USER"}
```

Update example:

```json
{"username":"member","email":"member@example.test","role":"ADMIN"}
```

A duplicate username/email returns `409`; invalid request data returns `400`; an unknown user returns `404`. Account management has no self-service route, and untrusted clients cannot set their role because only admins may use create/update. Protect admin credentials and the JWT secret as deployment secrets. Use TLS in any non-local environment because bearer tokens are credentials.

## Verify

The integration tests use H2 in Oracle compatibility mode and do not need an Oracle instance:

```sh
mvn test
```

They cover login and identity lookup, unauthenticated rejection, USER-to-ADMIN escalation/account-management denial, and ADMIN CRUD including checks that plaintext passwords are never returned or stored.
