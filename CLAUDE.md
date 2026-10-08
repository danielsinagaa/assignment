# Finshot Backend Take-Home — Remittance Transfer API

Take-home assignment (Java 17 target, Spring Boot 4.1.1, Maven wrapper, H2 in-memory). Not submitted: this file and `notes/`.

## Source of truth (read before changing anything)
- `notes/01-SPEC.md` — requirements (R-xx). Original: `[Finshot_ID] Backend Developer Remote Assignment.docx` (wins on conflict).
- `notes/02-DECISIONS.md` — design decisions (D-xx). Code must follow them; change the decision first, then the code.
- `notes/03-API-CONTRACT.md`, `notes/04-SCHEMA-DESIGN.md`, `notes/05-SCENARIOS.md` — contracts to test against.
- `notes/06-PLAN.md` — current phase; tick items only when verified.

## Rules
- The candidate must defend every line live in the interview: prefer plain, explicit code over clever abstractions; no Lombok;
  no features the spec gives no points for (auth, Swagger, Docker, list endpoints, metrics).
- Money: `BigDecimal`/`long` only, never `double`. Round once, in `TransferQuote`.
- Use the injected `Clock`, never `Instant.now()`.
- Every non-2xx response: `{ "error", "message" }` via the global handler.
- Deliverables (README.md, DESIGN.md, RESULT.md, code comments) in simple English. Talk to the user in Indonesian.
- RESULT.md content must come from real runs on a fresh app — never hand-edited or invented.
- Do not finish Part 2 work before Part 1 (PLAN phase 6) is complete.

## Commands
- Run: `mvnw.cmd spring-boot:run` (bash: `./mvnw spring-boot:run`)
- Test: `mvnw.cmd test`
