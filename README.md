# University Management System

A small Spring Boot REST API for managing admin, teacher, and student accounts. It uses PostgreSQL, session login, BCrypt passwords, and role-based access rules.

## Features and technology

- Create, list, view, update, and delete admins, teachers, and students. Admins cannot delete their own signed-in account.
- JSON requests and responses, input validation, and useful error codes.
- Spring Security session login, logout, CSRF protection, and BCrypt hashing.
- Simple HTML frontend with login, logout, an account table, and an add/edit form.
- Java 21, Spring Boot 4.1.2, Spring MVC, Spring Data JPA, PostgreSQL, Maven, and Docker Compose.

## Project structure

```text
src/main/java/com/university/management/
  ManagementApplication.java       starts Spring Boot
  controller/                     REST endpoints
  dto/                            request and safe response data
  exception/                      API errors
  model/                          Account entity and Role enum
  repository/                     database access
  security/                       login rules and optional demo data
  service/                        account CRUD logic
src/main/resources/application.properties
src/main/resources/static/         Simple frontend (HTML, CSS, JavaScript)
src/test/java/com/university/management/UniversityApiTests.java
src/test/java/com/university/management/AccountWorkflowIntegrationTests.java
src/test/java/com/university/management/service/AccountServiceTests.java
.github/workflows/tests.yml
TESTING_GUIDE.md
Dockerfile
compose.yaml
LAB_WALKTHROUGH.md
```

This follows MVC-style layers: the controller receives an HTTP request, the service applies business rules, the repository accesses the database, and a DTO controls the JSON response. A single `Account` entity stores all three roles. The URL and controller decide the role; clients cannot submit or change it.

## Role permissions

| Role | Admin API | Teacher API | Student API |
|---|---|---|---|
| ADMIN | CRUD | CRUD | CRUD |
| TEACHER | No access | Read and update own account | No access |
| STUDENT | No access | No access | Read and update own account |

Admins create and delete accounts. Teachers and students can view and edit their own account only. These rules are enforced by Spring Security and ownership checks in the controllers.

## Run with Docker Compose

Install Docker, then run from the project folder:

```bash
docker compose up --build
```

The API is at `http://localhost:8080`. Compose starts PostgreSQL first, waits for its health check, and stores its data in a named volume. The app connects to `db`, the Compose database service name. The passwords in `compose.yaml` are **development examples only**. Set `DB_PASSWORD`, `DEMO_ADMIN_PASSWORD`, `DEMO_TEACHER_PASSWORD`, and `DEMO_STUDENT_PASSWORD` in your environment before using this setup outside a local demo. Demo users are only created if their email does not already exist; changing an environment password does not reset an existing account.

## Run locally

Install Java 21 and PostgreSQL. Create a database named `university` and a database user with access to it. Set these environment variables and start the project:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/university'
$env:DB_USER = 'university'
$env:DB_PASSWORD = 'your-local-database-password'
$env:DEMO_USERS_ENABLED = 'true'
$env:DEMO_ADMIN_PASSWORD = 'Admin123!'
$env:DEMO_TEACHER_PASSWORD = 'Teacher123!'
$env:DEMO_STUDENT_PASSWORD = 'Student123!'
.\mvnw.cmd spring-boot:run
```

Use `./mvnw spring-boot:run` on macOS/Linux. The application creates or updates the simple database table through JPA. `DEMO_USERS_ENABLED` is `false` by default. For real deployments, use proper secrets and database migration management.

## Run in IntelliJ IDEA (quick lab demo)

1. In IntelliJ IDEA, choose **File → Open** and select the `management` project folder (the one containing `pom.xml`). Allow Maven to load the dependencies.
2. Set the project SDK to **Java 21 or newer** in **File → Project Structure → Project**.
3. Open `src/main/java/com/university/management/ManagementApplication.java`. Click the green gutter icon beside `main()` and choose **Modify Run Configuration**.
4. Set **Active profiles** to `local` (or put `--spring.profiles.active=local` in **Program arguments**), then click **Run**.
5. Open `http://localhost:8080` and sign in with `admin@university.test` / `Admin123!`.

The `local` profile uses an in-memory H2 database and creates three demo accounts. Its data resets each time the app stops. Leave this profile off when using the normal PostgreSQL setup or Docker Compose. If port 8080 is busy, stop the other server first.

## Frontend

Open `http://localhost:8080` after starting the application. Sign in with an existing account or one of the demo accounts below when demo users are enabled. The frontend is served by Spring Boot and needs no Node.js installation, npm dependencies, or separate development server.

Admins select which resource to manage: admins, teachers, or students. They can create, view, edit, and delete accounts in all three. Teachers and students see and edit only their own account. When editing, leave the password blank to keep it. Updating your own email or password requires logging in again.

All requests use the same-origin session cookie. The frontend fetches a CSRF token before login, logout, and account writes. Only the frontend assets and existing login/CSRF endpoints are public; account APIs remain protected.

## API endpoints

| Method | Endpoint | Meaning |
|---|---|---|
| GET | `/api/auth/csrf` | Get CSRF token and session cookie |
| POST | `/api/auth/login` | Log in with JSON email and password |
| POST | `/api/auth/logout` | Log out |
| GET | `/api/auth/me` | Current signed-in account (no password data) |
| GET, POST | `/api/admins`, `/api/teachers`, `/api/students` | List or create |
| GET, PUT, DELETE | `/api/admins/{id}`, `/api/teachers/{id}`, `/api/students/{id}` | View, update, or delete |

Create body: `{"name":"New Student","email":"new@university.test","password":"Secret123!"}`. Update body uses `name` and `email`, with an optional `password`. Passwords need at least 8 characters. Responses contain `id`, `name`, `email`, and `role`, never the password hash. Success codes are 200, 201, and 204. Errors use 400, 401, 403, 404, or 409 with an `error` message. Unsafe requests also require a CSRF token. A missing token on an anonymous protected request returns 401; an invalid or missing token in an authenticated session returns 403.

## Demo accounts

When Compose starts with the example environment values:

| Role | Email | Development password |
|---|---|---|
| ADMIN | `admin@university.test` | `Admin123!` |
| TEACHER | `teacher@university.test` | `Teacher123!` |
| STUDENT | `student@university.test` | `Student123!` |

These credentials are only for a local lab demonstration. Passwords are stored as BCrypt hashes in PostgreSQL.

## Try login and CRUD in PowerShell

Keep the same web session for the CSRF request, login, and later API calls:

```powershell
$base = 'http://localhost:8080'
$token = (Invoke-RestMethod "$base/api/auth/csrf" -SessionVariable session).token
$headers = @{ 'X-CSRF-TOKEN' = $token }
Invoke-RestMethod "$base/api/auth/login" -Method Post -WebSession $session -Headers $headers -ContentType 'application/json' -Body '{"email":"admin@university.test","password":"Admin123!"}'
Invoke-RestMethod "$base/api/students" -WebSession $session
Invoke-RestMethod "$base/api/students" -Method Post -WebSession $session -Headers $headers -ContentType 'application/json' -Body '{"name":"New Student","email":"new@university.test","password":"Secret123!"}'
Invoke-RestMethod "$base/api/auth/logout" -Method Post -WebSession $session -Headers $headers
```

Use the returned student `id` for `GET`, `PUT`, and `DELETE` on `/api/students/{id}`. To see the access rules, log in as a teacher and try `/api/students`: it returns 403. A protected GET without login returns 401.

## Tests

Run `./mvnw test` or `.\mvnw.cmd test`. The suite has six service unit tests and eleven API integration tests (three new workflow tests and eight existing API tests). Integration tests use an in-memory H2 database, so PostgreSQL is not needed. GitHub Actions shows the three new integration tests as three separate jobs, plus a unit-test job and an existing-API regression job. Each job uploads its Surefire report. See the [passing run](https://github.com/Siam-Kabir/University-Management/actions/runs/43691598445) and [TESTING_GUIDE.md](TESTING_GUIDE.md) for the test cases and the real bug reproduced and fixed in the commit history.
