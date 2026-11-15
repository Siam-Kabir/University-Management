# Testing guide for the lab demo

## What the two test layers prove

The six **unit tests** call `AccountService` directly with a mocked repository and password encoder. They check business rules without starting Spring or a database. The three **new integration tests** send HTTP requests through Spring Security, controllers, service, and an H2 database. Eight earlier API tests also run in the integration job. H2 keeps the suite independent of a running PostgreSQL server.

| Layer | Test case | Expected behavior |
|---|---|---|
| Unit | Create account | Trim name, lowercase email, hash password, assign the route's role |
| Unit | Duplicate on create | Reject an email already used without saving or hashing |
| Unit | Blank password on update | Keep the existing password hash |
| Unit | New password on update | Store a new hash |
| Unit | Another account's email on update | Reject the change and leave the account unchanged |
| Unit | Same email with different letter case | Normalize the current account's email without a false duplicate |
| Integration | Admin creates a student | Save a hashed password; the new student sees only their own record |
| Integration | Conflicting email update | Return HTTP 409 and leave the stored account unchanged |
| Integration | Existing mixed-case email update | Return HTTP 200 and store the lowercase version |

## A real red → green example

An older account can contain `Legacy@Example.com`. Updating that same account with `LEGACY@example.com` should normalize the email to `legacy@example.com`. The first version compared the stored and normalized strings case-sensitively. It then searched for a duplicate, found the current account itself, and returned HTTP 409.

1. `9c5adac test(account): reproduce false duplicate on email case change` added one unit and one integration regression check. Before the fix, the unit test threw `DuplicateEmailException` and the API test got HTTP 409 instead of 200.
2. `9654c0d fix(account): allow case normalization of current email` changed the comparison to ignore letter case. The duplicate-email tests still protect against using **another** account's email.
3. The full suite then passed: 6 unit tests + 11 integration tests = 17 tests, with 0 failures or errors.

This is a regression test: it records a real fault so the same behavior cannot silently return later.

For a quick presentation, run `git show --stat 9c5adac` to point to the tests, then `git show 9654c0d` to show the one-line production fix. Explain that the same-email test changed from a 409 error to a successful update, while the other-account duplicate test stayed green.

## Run the tests

Use Java 21 or newer. On macOS or Linux:

```bash
bash ./mvnw --batch-mode -Dtest=AccountServiceTests test
bash ./mvnw --batch-mode -Dtest=AccountWorkflowIntegrationTests test
bash ./mvnw --batch-mode -Dtest=UniversityApiTests test
```

On Windows PowerShell, use `.\mvnw.cmd` in place of `bash ./mvnw`. Run `./mvnw test` (or `.\mvnw.cmd test`) for all 17 tests together. Surefire writes detailed results to `target/surefire-reports/`.

The GitHub Actions workflow shows five jobs: one unit job for six tests, three integration jobs for the three cases in the table above, and one regression job for the eight older API tests. Each integration job runs one method from `AccountWorkflowIntegrationTests` and uploads its own Surefire report. A job is a CI container; it is not the same as a test method.

[This GitHub Actions run](https://github.com/Siam-Kabir/University-Management/actions/runs/43691598445) passed all five jobs: six unit tests, the three individually named new integration tests, and eight existing API regression tests. Earlier red runs were rejected before any test step started; they are not examples of a failing test. Use the regression commits and the local test output above to explain the real red → green sequence.
