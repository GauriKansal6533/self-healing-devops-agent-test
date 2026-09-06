# 🤖 Self-Healing Autonomous DevOps Agent

A working prototype that detects a failing test, diagnoses the bug using an LLM, generates and applies a fix, verifies it independently, and pushes the verified fix back to GitHub. The agent can automatically diagnose, patch, re-test, and push a verified fix without manual intervention during the repair cycle.

```
GitHub push → Webhook → Signature verification → Docker test run →
Failure parsed → Gemini generates patch → Patch sanity-checked →
Patch applied → Tests re-verified in Docker → Fix committed & pushed
```

## Why this project exists

Most portfolio projects are CRUD apps. This one demonstrates three things together that rarely show up in the same project: **event-driven backend engineering**, **container-based isolation for safe untrusted code execution**, and **applied LLM orchestration** — an AI model doing bounded, independently-verified work inside a larger system, not just answering chat prompts.

## Architecture

```
GitHub
  │  push event
  ▼
Webhook
  │
  ▼
Spring Boot Agent (webhook receiver)
  │
  ▼
HMAC-SHA256 Signature Verification
  │
  ▼
Git Clone (fresh temp directory per run)
  │
  ▼
Docker Test Runner (mvn test, isolated container)
  │
  ├── Tests Pass ──────────────────────────────┐
  │                                             │
  ▼ Tests Fail                                  │
Failure Parser (regex → structured data)        │
  │                                             │
  ▼                                             │
Gemini (via LangChain4j)                        │
  │                                             │
  ▼                                             │
Patch Validation (sanity checks)                │
  │                                             │
  ▼                                             │
Patch Application (write to file)               │
  │                                             │
  ▼                                             │
Docker Re-test (independent verification)       │
  │                                             │
  ├── Still Failing → retry (max 2 attempts)    │
  │                                             │
  ▼ Pass                                        │
Git Commit + Push ◄──────────────────────────────┘
  │
  ▼
GitHub (verified fix)
```

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.1.0, Spring Framework 7 |
| Build tool | Maven |
| Containerization | Docker (`maven:3.9-eclipse-temurin-21` image) |
| LLM orchestration | LangChain4j 1.18.1 |
| LLM provider | Google Gemini (`gemini-3.5-flash-lite`) |
| JSON handling | Jackson |
| Webhook security | HMAC-SHA256 signature verification |
| Version control automation | Git CLI via `ProcessBuilder`, GitHub PAT auth |
| Local tunneling (dev) | ngrok |

## How it works, step by step

1. **Webhook reception** — A Spring Boot `@RestController` receives GitHub's `push` webhook.
2. **Signature verification** — The raw request body is verified against the `X-Hub-Signature-256` header using HMAC-SHA256 and a shared secret (`GITHUB_WEBHOOK_SECRET`), with a constant-time comparison (`MessageDigest.isEqual`) to avoid timing attacks. Requests that fail verification are rejected before any further processing.
3. **Dynamic cloning** — The agent reads the repo's clone URL and branch from the verified payload and performs a shallow (`--depth 1`), single-branch clone into a fresh, disposable temp directory.
4. **Isolated test execution** — `mvn test` runs inside a disposable Docker container (invoked via `ProcessBuilder`), mounting the cloned repo.
5. **Structured failure parsing** — If tests fail, a regex-based parser extracts the failing test's class, method, exception type, message, and file/line from the Maven/Surefire output.
6. **LLM patch generation** — The structured failure plus the relevant source file are sent to Gemini via LangChain4j's `ChatModel` abstraction, with strict prompt constraints requiring raw, complete, corrected Java source with no commentary or markdown.
7. **Patch sanity checks** — Before being trusted, the generated patch is checked for basic plausibility (balanced braces, presence of a class/interface declaration, no leftover markdown fences) and the resolved file path is validated to ensure it stays within the repository root.
8. **Patch applied + independently re-verified** — The patch is written to disk, and the **same isolated Docker test suite** is re-run against it. **The LLM's generated fix is not trusted blindly — the fix must independently pass the test suite inside Docker before it is committed and pushed.**
9. **Retry with fresh context** — If the patch doesn't fix the issue, the agent retries once (2 attempts total), feeding the LLM the *new* failure output from the failed attempt rather than stale data.
10. **Automated commit + push** — Once tests pass, the agent commits the fix and pushes it back to the source branch, authenticated via a GitHub PAT using `git -c http.extraheader=...`, with the token never written to disk or logged.

## Demonstrated self-healing run

The patient/test repository contained an intentionally broken method:

```java
public int add(int a, int b) {
    return 2;
}
```

against a test expecting:

```java
assertEquals(5, app.add(2, 3));
```

The agent detected the failing test, generated a corrected implementation:

```java
public int add(int a, int b) {
    return a + b;
}
```

re-ran the test suite inside Docker, confirmed it passed, and automatically committed and pushed the fix to GitHub:

```
0910d01 Auto-fix: PatientAppApplicationTests.additionShouldWork (org.opentest4j.AssertionFailedError)
```

## Security considerations

- **Webhook signature verification** — every incoming webhook is verified via HMAC-SHA256 before processing; unsigned or incorrectly signed requests are rejected.
- **No secrets in source code** — `GEMINI_API_KEY`, `GITHUB_TOKEN`, and `GITHUB_WEBHOOK_SECRET` are all supplied via environment variables, never hardcoded or committed.
- **No credentials in process arguments or logs** — the GitHub PAT is passed to git via a config-scoped `http.extraheader`, not embedded in a URL, and is never logged.
- **Path containment check** — before any file write, the resolved source file path is validated to ensure it stays within the cloned repository root.
- **Patch never trusted blindly** — generated code passes a sanity check before being written, and must independently pass the Docker test suite before any commit happens.

## Known limitations & engineering tradeoffs

- **Limited source-file discovery** — the agent does not perform full repository-wide program analysis to map a failing test to its responsible source file; it currently targets a known application file rather than tracing test-to-source relationships generically.
- **Java/Maven-focused failure parsing** — the regex-based failure parser is built around Maven/Surefire output specifically.
- **Lightweight patch validation** — sanity checks (brace balance, markdown detection, path containment) rather than full semantic or compilation verification prior to the test re-run.
- **Docker invoked via `ProcessBuilder`** rather than a typed Docker SDK — a deliberate simplicity tradeoff.
- **Retry limit of 2 attempts** — bounds LLM API usage and prevents indefinite loops on an unfixable bug.
- **Local ngrok dependency** for the development/demo setup, since the agent isn't deployed to a public endpoint.
- **Container hardening headroom** — Docker execution could be further hardened with resource limits, network restrictions, read-only mounts, non-root execution, and timeouts.

## Future improvements

- Multi-file autonomous bug diagnosis
- Repository-wide source analysis
- AST-based patch validation
- Structured/function-calling LLM output instead of prompt-level formatting instructions
- Docker SDK integration in place of `ProcessBuilder`
- Docker resource limits and execution timeouts
- Support for Gradle, npm, Python, and other build systems
- Persistent failure history
- Observability/metrics dashboard
- Pull-request-based repair workflow instead of direct-to-branch commits
- Human approval mode for high-risk fixes
- Stronger sandboxing

## Local setup

1. JDK 21, Docker Desktop, and Maven installed
2. Clone this repo and run `mvn clean compile`
3. Get a free Gemini API key at [ai.google.dev/gemini-api/docs/api-key](https://ai.google.dev/gemini-api/docs/api-key)
4. Create a GitHub Personal Access Token (`repo` scope) for push access
5. Generate a webhook secret and set it on your test repo's webhook configuration
6. Set environment variables: `GEMINI_API_KEY`, `GITHUB_TOKEN`, `GITHUB_WEBHOOK_SECRET`
7. Run the app: `mvn spring-boot:run`
8. Expose it locally with `ngrok http 8080` and register `<ngrok-url>/webhook/github` as a webhook on a test repo, subscribed to `push` events, with the secret from step 5 configured

## Project structure

```
src/main/java/com/gauri/self_healing_devops_agent/
├── controller/
│   └── GitHubWebhookController.java   # Webhook entry point, orchestrates the pipeline
├── model/
│   ├── GitHubPushPayload.java         # Deserialized webhook payload
│   └── TestFailure.java               # Structured parsed failure data
└── service/
    ├── WebhookSignatureVerifier.java  # HMAC-SHA256 verification
    ├── GitCloneService.java           # Dynamic clone + cleanup
    ├── DockerTestRunnerService.java   # Isolated mvn test execution
    ├── TestFailureParserService.java  # Regex-based failure extraction
    ├── SourceFileReaderService.java   # Locate/read/write source files
    ├── PatchGenerationService.java    # LangChain4j + Gemini integration
    └── GitCommitPushService.java      # Automated commit + push
```

## Key engineering concepts demonstrated

- **Event-driven architecture** via GitHub webhooks instead of polling
- **Dependency injection / IoC** via Spring's `@Autowired` and `@Service` beans
- **Process isolation** via Docker for untrusted code execution
- **Structured signal extraction** via regex, reducing noisy logs to precise data before LLM input
- **LLM tool orchestration** via LangChain4j's `ChatModel` abstraction, keeping the provider swappable
- **Verify-before-trust design** — the core principle of the project: an LLM's output is a hypothesis, not a guaranteed result, until independently re-tested
- **Cryptographic authentication** via HMAC-SHA256 webhook signatures with constant-time comparison
- **Credential hygiene** — environment-based secrets, no tokens in URLs, logs, or source

## Project status

This is a working end-to-end prototype demonstrating autonomous test-failure repair on a small Java/Maven project. It is not a production-ready platform, and it has not been evaluated for reliability, scale, or generalization beyond the scenarios described above. The value of the project is in demonstrating the architecture and engineering discipline — isolation, verification-before-trust, and credential hygiene — behind an LLM-integrated autonomous system.

## 👩‍💻 Author

**Gauri Kansal**

B.Tech Computer Science

Built as an exploration of autonomous DevOps, LLM-powered software engineering, and event-driven backend systems.
