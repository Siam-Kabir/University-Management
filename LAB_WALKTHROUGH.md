# Lab walkthrough: University Management System

## A. Project overview

This application manages three kinds of university accounts: admins, teachers, and students. Each account resource has REST endpoints for create, read, update, and delete, with permissions depending on the signed-in role. Spring Boot starts the web server, creates the Spring components, and configures the database and security libraries from the project dependencies.

Browser page → JavaScript `fetch()` → Spring Security → Controller → AccountService → AccountRepository → database. The result returns as JSON, and JavaScript updates the page. Spring Boot serves both the browser files and the API on port 8080, so no separate frontend server is needed.

## B. Important folders and files

- `ManagementApplication.java` has `main()`, which starts Spring Boot.
- `model/Account.java` is a JPA entity. An entity is a Java class mapped to a database table. Its fields become account columns. `Role.java` lists the three allowed roles.
- `repository/AccountRepository.java` extends `JpaRepository`. Spring Data JPA supplies the usual database methods and builds queries from method names such as `findByEmailIgnoreCase`.
- `service/AccountService.java` contains CRUD logic. It checks duplicate emails, chooses accounts of the requested role, hashes passwords, and converts entities to safe response DTOs.
- `controller/StudentController.java` receives `/api/students` HTTP requests and calls `AccountService` with `Role.STUDENT`. `TeacherController` and `AdminController` do the same for their resources. Controllers do not write database queries.
- `controller/AuthController.java` provides a CSRF token and checks login credentials. Spring Security's logout filter handles `/api/auth/logout`.
- `dto/` contains JSON request and response records. `AccountResponse` leaves out the password hash. The client cannot provide a `role` in a request DTO.
- `exception/ApiExceptionHandler.java` turns validation, duplicate email, and missing account problems into useful HTTP errors.
- `security/SecurityConfig.java` defines the BCrypt encoder, account lookup for login, session storage, and URL permission rules. `DemoDataInitializer.java` optionally creates three lab accounts.
- `application.properties` reads database and demo settings from environment variables.
- `application-local.properties` provides an in-memory H2 database and demo users for running directly in IntelliJ.
- `static/index.html` and `static/assets/` are the browser UI served by Spring Boot. The JavaScript calls the REST API using the login session and CSRF token.
- `Dockerfile` builds the JAR and runs it in a Java container. `compose.yaml` runs the app and PostgreSQL together.

There is one account table rather than three almost identical tables. The `role` column distinguishes the three resources. This keeps the lab project small and gives every email one unique login.

## C. How CRUD works

Example: an admin creates a student.

1. The client sends `POST /api/students` with JSON containing `name`, `email`, and `password`, plus its session cookie and CSRF token.
2. `SecurityConfig.securityFilterChain()` checks that the session has the ADMIN role. Validation checks the request fields.
3. `StudentController.create()` calls `AccountService.create(request, Role.STUDENT)`.
4. `AccountService.create()` checks whether the email exists, hashes the password with BCrypt, and creates an `Account` whose role is STUDENT.
5. `AccountRepository.save()` writes the row to PostgreSQL.
6. `AccountResponse.from()` builds safe response data. The controller returns HTTP 201 and a `Location` header.

For reading, `findAll()` or `findById()` calls repository queries filtered by role. For updating, `update()` changes the name, email, and optional password. For deleting, `delete()` removes the matching account. An admin cannot delete the account they are currently signed in with. A missing ID returns 404.

## D. How login works

1. The client calls `GET /api/auth/csrf` to obtain a CSRF token and session cookie. It sends both on login and other POST, PUT, or DELETE requests.
2. The client posts email and password as JSON to `/api/auth/login`.
3. `AuthController.login()` asks Spring Security's `AuthenticationManager` to authenticate the user.
4. `SecurityConfig.userDetailsService()` loads the account by email. Spring Security compares the submitted password with the stored BCrypt hash using the `PasswordEncoder`.
5. Successful login stores the security context in the HTTP session. Later requests use the session cookie. A wrong password returns 401.
6. `POST /api/auth/logout` clears the authentication and invalidates the session. It returns 204.

The database never stores or sends a plain-text password. Demo passwords come from environment settings and are hashed when the demo accounts are created.

## E. How role-based security works

`SecurityConfig.securityFilterChain()` has URL and HTTP method rules. Only ADMIN can create or delete accounts. ADMIN can read and update any account. TEACHER can read and update only their own teacher account; STUDENT can read and update only their own student account. `AccountAccess` applies the ownership checks in the controllers. An admin cannot delete their own signed-in account. A teacher asking for `/api/students` gets 403. A visitor without login gets 401 for a protected request. CSRF separately rejects unsafe authenticated requests without a valid token with 403. Controllers pass a fixed `Role` to the service, so a client cannot change a role by adding a field to JSON.

## F. How Docker works

`Dockerfile` first uses Maven with Java 21 to compile the application. A second, smaller Java image runs the built JAR on port 8080. `compose.yaml` starts PostgreSQL in a separate container because the database is a separate service with persistent storage. The database data survives container restarts in the `postgres_data` volume. Compose waits until PostgreSQL reports healthy before starting the app. Inside Compose, `DB_URL` points to `db:5432`; `db` is the PostgreSQL service name. Environment variables supply the database password and optional demo passwords.

## G. Viva questions and short answers

1. **What is Spring Boot?** It is a framework that starts and configures a Spring application with less manual setup.
2. **What is a REST API?** It exposes resources through URLs and HTTP methods such as GET, POST, PUT, and DELETE.
3. **What does `@RestController` do?** It marks a class whose methods handle web requests and return response data, usually JSON.
4. **Why is there a service class?** It keeps business rules out of controllers and in one place.
5. **What does `AccountRepository` do?** It reads and writes account rows through Spring Data JPA.
6. **What is an entity?** It is a Java object mapped to a database table by JPA.
7. **Why is there one `Account` entity?** All three roles need the same fields, and one table keeps emails unique and login simple.
8. **Where is a new account's role chosen?** The controller passes a fixed role to `AccountService.create()`; the request cannot choose it.
9. **Why use DTOs?** They validate requests and prevent the password hash from appearing in responses.
10. **What does BCrypt do?** It turns a password into a salted, slow hash and verifies a submitted password against that hash.
11. **Where is password hashing performed?** `AccountService.create()` and `update()` call `PasswordEncoder.encode()`; demo setup does the same.
12. **How does login find a user?** `userDetailsService()` looks up the email with `AccountRepository.findByEmailIgnoreCase()`.
13. **How does a session work?** The server stores the authenticated security context, and the browser sends a session cookie on later requests.
14. **Why is a CSRF token needed?** It stops another site from making state-changing requests using the user's session cookie.
15. **What happens when a teacher requests student data?** The URL rule rejects the teacher role and returns 403.
16. **What is the difference between 401 and 403?** 401 means login is required or failed. 403 means the request is forbidden, including a missing CSRF token.
17. **What does 404 mean here?** No account with that ID exists in the requested role resource.
18. **What does 409 mean here?** The email is already used by another account.
19. **Why use PostgreSQL?** It persists application data in a relational database.
20. **Why use Docker Compose?** It starts the Java app and PostgreSQL together with the correct connection settings.
