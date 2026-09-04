package com.gauri.self_healing_devops_agent.controller;

import com.gauri.self_healing_devops_agent.model.GitHubPushPayload;
import com.gauri.self_healing_devops_agent.model.TestFailure;
import com.gauri.self_healing_devops_agent.service.DockerTestRunnerService;
import com.gauri.self_healing_devops_agent.service.GitCloneService;
import com.gauri.self_healing_devops_agent.service.PatchGenerationService;
import com.gauri.self_healing_devops_agent.service.SourceFileReaderService;
import com.gauri.self_healing_devops_agent.service.TestFailureParserService;
import com.gauri.self_healing_devops_agent.service.GitCommitPushService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/webhook")
public class GitHubWebhookController {

    private static final Logger logger =
            LoggerFactory.getLogger(GitHubWebhookController.class);
    @Autowired
private GitCommitPushService gitCommitPushService;

    
    @Autowired
    private GitCloneService gitCloneService;

    @Autowired
    private DockerTestRunnerService dockerTestRunnerService;

    @Autowired
    private TestFailureParserService testFailureParserService;

    @Autowired
    private SourceFileReaderService sourceFileReaderService;

    @Autowired
    private PatchGenerationService patchGenerationService;

    @PostMapping("/github")
    public String handleGitHubWebhook(
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestBody(required = false) GitHubPushPayload payload) {

        logger.info("Received GitHub event: {}", eventType);

        if ("push".equals(eventType)
                && payload != null
                && payload.getRepository() != null) {

            String cloneUrl =
                    payload.getRepository().getCloneUrl();

            String branch =
                    payload.getRef()
                            .replace("refs/heads/", "");

            logger.info(
                    "Push event on repo={}, branch={}",
                    payload.getRepository().getFullName(),
                    branch
            );

            Path clonedRepoPath = null;

            try {

                // Step 1: Clone repository
                clonedRepoPath =
                        gitCloneService.cloneRepo(
                                cloneUrl,
                                branch
                        );

                // Step 2: Run tests
                DockerTestRunnerService.TestResult result =
                        dockerTestRunnerService.runTests(
                                clonedRepoPath
                        );

                // Step 3: Check result
                if (result.success()) {

                    logger.info("✅ Tests passed!");

                } else {

                    logger.warn(
                            "❌ Tests failed. Parsing failure details..."
                    );

                    // Step 4: Parse failures
                    List<TestFailure> failures =
                            testFailureParserService.parse(
                                    result.logOutput()
                            );

                    for (TestFailure failure : failures) {

                        logger.warn(
                                "Parsed failure: class={}, method={}, " +
                                        "errorType={}, message={}, file={}, line={}",
                                failure.testClass(),
                                failure.testMethod(),
                                failure.errorType(),
                                failure.errorMessage(),
                                failure.failingFile(),
                                failure.failingLine()
                        );
                    }

                    // Step 5: Start self-healing
                    if (!failures.isEmpty()) {

                        boolean healed =
        attemptSelfHeal(
                failures.get(0),
                clonedRepoPath,
                cloneUrl,
                branch,
                1
        );

                        if (healed) {

                            logger.info(
                                    "🎉 Self-healing succeeded!"
                            );

                        } else {

                            logger.error(
                                    "💀 Self-healing failed after retry. " +
                                            "Manual intervention required."
                            );
                        }
                    }
                }

            } catch (Exception e) {

                logger.error(
                        "Error during clone/test pipeline",
                        e
                );

            } finally {

                // Always cleanup cloned repository
                if (clonedRepoPath != null) {

                    gitCloneService.cleanup(
                            clonedRepoPath
                    );
                }
            }
        }

        return "Webhook received";
    }


    /**
     * Attempts to automatically fix a failed test.
     *
     * Maximum attempts = 2.
     */
    private boolean attemptSelfHeal(
        TestFailure failure,
        Path repoPath,
        String cloneUrl,
        String branch,
        int attemptNumber) {

        final int MAX_ATTEMPTS = 2;

        logger.info(
                "🔧 Self-heal attempt {} of {}",
                attemptNumber,
                MAX_ATTEMPTS
        );

        try {

            /*
             * Currently we know the Phase 0 patient application
             * uses this main application class.
             */
            String mainAppFileName =
                    "PatientAppApplication.java";

            // Find source file path
            Path sourceFilePath =
                    sourceFileReaderService.findSourceFilePath(
                            repoPath,
                            mainAppFileName
                    );

            // Read current source code
            String buggySource =
                    sourceFileReaderService.findAndReadSourceFile(
                            repoPath,
                            mainAppFileName
                    );

            logger.info(
                    "Source file loaded: {}",
                    sourceFilePath
            );

            // Ask Gemini for a patch
            logger.info(
                    "Generating patch using Gemini (attempt {})...",
                    attemptNumber
            );

            String patch =
                    patchGenerationService.generatePatch(
                            failure,
                            buggySource
                    );

            logger.info(
                    "=== Generated Patch (attempt {}) ===\n{}",
                    attemptNumber,
                    patch
            );

            logger.info(
                    "=== End Generated Patch ==="
            );

            // Write patch to source file
            sourceFileReaderService.writeSourceFile(
                    sourceFilePath,
                    patch
            );

            logger.info(
                    "✏️ Patch written to {}",
                    sourceFilePath
            );

            // Re-run tests
            logger.info(
                    "🧪 Re-running tests to verify the patch..."
            );

            DockerTestRunnerService.TestResult retestResult =
                    dockerTestRunnerService.runTests(
                            repoPath
                    );

            // Check retest
            if (retestResult.success()) {

    logger.info("✅ Tests passed after patch!");

    String commitMessage = String.format(
            "Auto-fix: %s.%s (%s)",
            failure.testClass(),
            failure.testMethod(),
            failure.errorType()
    );

    boolean pushed =
            gitCommitPushService.commitAndPush(
                    repoPath,
                    cloneUrl,
                    branch,
                    commitMessage
            );

    if (pushed) {

        logger.info(
                "✅ Fix committed and pushed to {}!",
                branch
        );

    } else {

        logger.warn(
                "⚠️ Tests passed but commit/push failed."
        );
    }

    return true;
}

            logger.warn(
                    "❌ Tests still failing after attempt {}.",
                    attemptNumber
            );

            // Retry only once
            if (attemptNumber < MAX_ATTEMPTS) {

                List<TestFailure> newFailures =
                        testFailureParserService.parse(
                                retestResult.logOutput()
                        );

                if (!newFailures.isEmpty()) {

                    logger.info(
                            "🔄 Starting retry attempt {}...",
                            attemptNumber + 1
                    );

                    return attemptSelfHeal(
        newFailures.get(0),
        repoPath,
        cloneUrl,
        branch,
        attemptNumber + 1
);
                }
            }

            return false;

        } catch (Exception e) {

            logger.error(
                    "Error during self-heal attempt {}",
                    attemptNumber,
                    e
            );

            return false;
        }
    }
}