# AGENTS.md — Coding Agent Instructions for flowset-demo

## Project Overview

`flowset-demo` is a **Java 17 / Spring Boot 3.4.4 / Operaton 1.1.1 BPM** backend application.
It demonstrates BPMN process automation with DMN decision tables, file-based H2 storage, and
the Operaton REST API + Webapp UI. There is no frontend; all UI is provided by Operaton's
built-in Cockpit/Tasklist/Admin web apps.

---

## Build & Run Commands

All commands use the **Gradle wrapper** (`./gradlew`). Do not use `mvn` or a global `gradle`.

| Action | Command |
|---|---|
| Run application | `./gradlew bootRun` |
| Build (compile + test) | `./gradlew build` |
| Compile only | `./gradlew compileJava` |
| Run all tests | `./gradlew test` |
| Clean + full rebuild | `./gradlew clean build` |

### Running a Single Test

Use the `--tests` filter with a fully-qualified class or method name:

```bash
# Run a single test method
./gradlew test --tests "io.flowset.FlowsetDemoApplicationTests.contextLoads"

# Run all tests in a class
./gradlew test --tests "io.flowset.FlowsetDemoApplicationTests"

# Run all tests matching a pattern
./gradlew test --tests "io.flowset.*"
```

Add `--info` or `--stacktrace` for verbose test output.

---

## Project Structure

```
src/main/java/io/flowset/
├── FlowsetDemoApplication.java     # Spring Boot entry point (@SpringBootApplication)
├── WebSecurityConfiguration.java   # Security and CORS config
├── delegate/                       # Operaton JavaDelegate service-task implementations
├── model/                          # Immutable data models (Java records)
├── repository/                     # Data access layer (in-memory ConcurrentHashMap)
└── variable/                       # VariableConstants — string keys for process variables

src/main/resources/
├── application.properties          # App config (H2, Operaton admin creds)
├── processes/                      # BPMN workflow definitions
├── decisions/                      # DMN decision tables
└── process-forms/                  # Operaton embedded form schemas (JSON)

src/test/java/io/flowset/           # JUnit 5 integration tests
```

---

## Code Style Guidelines

### Language & Java Version
- **Java 17**. Use modern Java features where they improve clarity:
  - Prefer **records** for immutable value/data objects
  - Use `instanceof` pattern matching where appropriate
  - Do **not** use `var` — declare types explicitly for readability
  - Prefer switch expressions over traditional `switch` with `break` for new code

### Naming Conventions

| Element | Convention | Example |
|---|---|---|
| Packages | lowercase, domain-reversed | `io.flowset.delegate` |
| Classes / Interfaces | PascalCase | `ScoringDelegate`, `ApplicantRepository` |
| Methods | camelCase | `findById`, `execute` |
| Variables / fields | camelCase | `applicantId`, `creditScore` |
| Constants | UPPER_SNAKE_CASE | `APPLICANT_ID`, `CREDIT_SCORE` |
| BPMN process files | kebab-case with version suffix | `loan-scoring-v1.bpmn` |
| DMN / form files | kebab-case with version suffix | `loan-decision.dmn`, `start-form-v1.form` |
| BPMN element IDs | snake_case | `loan_applicant_data`, `calculate_score` |

### Formatting
- **4-space indentation** (no tabs)
- Opening braces on the same line as declarations (K&R style)
- Single blank line between methods; no trailing blank lines inside methods
- No wildcard imports (`import java.util.*` is forbidden)
- Import order: `java.*`, `javax.*`, then third-party (`org.operaton.*`, `org.slf4j.*`, `org.springframework.*`), then project-internal (`io.flowset.demo.*`)

### Types
- Use **primitive types** (`int`, `double`, `boolean`) in business logic; avoid unnecessary boxing
- Avoid raw types and unchecked casts; if a Operaton variable cast is unavoidable, add a
  comment explaining the contract
- Use `Optional` only at API boundaries where absence is a meaningful return value, not as a field type

### Logging
- **Lombok is not on the classpath** — do not use `@Slf4j`. Declare loggers manually:
  ```java
  private static final Logger log = LoggerFactory.getLogger(MyClass.class);
  ```
- Use parameterized messages: `log.info("Applicant {} approved", applicantId)` — never string concatenation
- Log meaningful business events at `INFO`; debug details at `DEBUG`

---

## Operaton / BPM Patterns

### JavaDelegate
- Each service task gets its own `JavaDelegate` implementation in `delegate/`
- Annotate with `@Component` so Spring manages the bean lifecycle; Operaton resolves it by
  bean name via `camunda:delegateExpression="${myDelegate}"`
- Implement `execute(DelegateExecution execution)` and declare `throws Exception`
- Read process variables via `execution.getVariable(VariableConstants.KEY)` — always use
  constants from `VariableConstants`, never inline strings
- Write results back via `execution.setVariable(VariableConstants.KEY, value)`
- Note: `SayHelloDelegate` intentionally omits `@Component` and is referenced via
  `camunda:class` (direct instantiation) — treat this as an exception, not the pattern to follow

### Dependency Injection in Delegates
- Use **constructor injection** for required collaborators (e.g. repositories); avoid field
  injection with `@Autowired` in new code

### Process Variables
- All process-variable string keys are centralised in `variable/VariableConstants.java` as
  `public static final String` constants
- Add new variable keys to `VariableConstants` before referencing them in delegates

### BPMN / DMN Files
- Store BPMN files in `src/main/resources/processes/`
- Store DMN files in `src/main/resources/decisions/`
- Version file names with a `-vN` suffix (e.g., `loan-scoring-v2.bpmn`) when making breaking changes

---

## Error Handling

- **Validate inputs early**: null-check process variables at the top of `execute()` and throw
  `RuntimeException` with a descriptive message if missing:
  ```java
  if (applicantId == null) {
      throw new RuntimeException("applicantId is not set");
  }
  ```
- **No custom exception hierarchy** exists; use `RuntimeException` or `IllegalArgumentException`
- Do **not** silently swallow exceptions — let them propagate to the Operaton engine, which
  creates an incident for operational visibility
- Do **not** use empty `catch` blocks

---

## Testing

- Framework: **JUnit 5** (Jupiter) + **Spring Boot Test** (`@SpringBootTest`)
- Test class names end in `Tests` (e.g., `ApplicantRepositoryTests`)
- Use `@SpringBootTest` for integration/context tests; prefer plain unit tests (no Spring
  context) for delegates and repositories where possible
- No mocking library is currently configured; add **Mockito** (`mockito-junit-jupiter`) to
  `build.gradle` if unit-testing delegates that require mocks
- No Operaton process-test utilities are configured; add `operaton-bpm-assert` if writing
  process-level tests

---

## Dependencies & Configuration

- Add dependencies to `build.gradle` under the correct configuration (`implementation`,
  `testImplementation`, `runtimeOnly`)
- Operaton starters (`operaton-bpm-spring-boot-starter*`) are pinned at **1.1.1** — keep all
  three starters and `operaton-spin-dataformat-all` on the same version
- Sensitive values (passwords, API keys) go in `application.properties` only as placeholders;
  real secrets must not be committed; admin credentials `admin`/`admin` are demo-only

---

## What to Avoid

- Do not add a frontend build pipeline (npm, webpack, etc.) unless explicitly required
- Do not introduce a JPA/Hibernate ORM layer without discussion — the current in-memory
  repository is intentional for demo simplicity
- Do not use `System.out.println` for logging — always use SLF4J
- Do not commit H2 database files (`h2/` directory is gitignored)
- Do not hardcode Operaton process variable names as string literals outside `VariableConstants`
- Do not add `@Slf4j` — Lombok is not a project dependency
