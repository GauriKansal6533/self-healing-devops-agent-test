# patient-app (Demo Target Repository)

This is a small, intentionally simple Java/Spring Boot project used as the **test target** for my [Self-Healing Autonomous DevOps Agent](https://github.com/GauriKansal6533/self-healing-devops-agent).

It has no purpose on its own — it exists to be **broken, detected, diagnosed, patched, and verified** by that agent.

## What happened here

This repo's commit history contains a real, autonomously-generated fix, produced end-to-end with no manual intervention during the repair cycle:

```
Auto-fix: PatientAppApplicationTests.additionShouldWork (org.opentest4j.AssertionFailedError)
```

The sequence that produced it:
1. A test was pushed expecting `add(2, 3)` to return `5`
2. The actual implementation had a deliberate bug, returning a hardcoded wrong value
3. The self-healing agent detected the failing test via a GitHub webhook
4. It diagnosed the bug using Google Gemini (via LangChain4j) and generated a corrected implementation
5. It re-ran the test suite inside an isolated Docker container to independently verify the fix
6. Only after the fix passed did the agent commit and push it back here, automatically

## Where the real engineering lives

The actual agent — the Spring Boot webhook receiver, Docker orchestration, LLM integration, and security hardening — is in the main repository:

**➡️ [github.com/GauriKansal6533/self-healing-devops-agent](https://github.com/GauriKansal6533/self-healing-devops-agent)**

That repo's README has the full architecture, tech stack, and demonstrated workflow.
