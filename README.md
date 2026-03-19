# Tutoring App (Java + Spring Boot)

A ready-to-ship tutoring management app with:
- Tutor management
- Student management
- Session booking and cancellation
- Scheduling conflict protection for both tutors and students
- Web UI (Thymeleaf) and REST API

## Tech Stack
- Java 17
- Spring Boot 3.4.3
- Spring Web + Spring Data JPA + Validation + Thymeleaf
- H2 in-memory database

## Run the app
1. Install Java 17 and Gradle.
2. From the project root, run:

```bash
gradle bootRun
```

3. Open:
- `http://localhost:8080` (web app)
- `http://localhost:8080/h2-console` (DB console)

H2 connection settings:
- JDBC URL: `jdbc:h2:mem:tutoringdb`
- User: `sa`
- Password: (blank)

## Run tests
```bash
gradle test
```

## Build a production jar
```bash
gradle clean bootJar
```

## Main pages
- `/` Dashboard
- `/tutors` Add/list tutors
- `/students` Add/list students
- `/sessions` Book/cancel sessions

## REST API
- `GET /api/tutors`
- `GET /api/students`
- `GET /api/sessions`
- `GET /api/sessions/scheduled`
- `POST /api/sessions`
- `POST /api/sessions/{id}/cancel`

### Book session payload example
```json
{
  "tutorId": 1,
  "studentId": 1,
  "subject": "MATH",
  "startsAt": "2026-03-20T16:00:00",
  "durationMinutes": 60,
  "notes": "Algebra review"
}
```
