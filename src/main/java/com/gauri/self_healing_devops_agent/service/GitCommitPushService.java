
package com.gauri.self_healing_devops_agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;

@Service
public class GitCommitPushService {

    private static final Logger logger =
            LoggerFactory.getLogger(GitCommitPushService.class);

    @Value("${github.token}")
    private String githubToken;

    public boolean commitAndPush(
            Path repoPath,
            String cloneUrl,
            String branch,
            String commitMessage) {

        try {

            // Configure commit identity
            runGitCommand(
                    repoPath,
                    "config",
                    "user.name",
                    "Self-Healing Agent"
            );

            runGitCommand(
                    repoPath,
                    "config",
                    "user.email",
                    "agent@self-healing-devops.local"
            );

            // Stage all changes
            runGitCommand(
                    repoPath,
                    "add",
                    "-A"
            );

            // Create commit
            int commitExit =
                    runGitCommand(
                            repoPath,
                            "commit",
                            "-m",
                            commitMessage
                    );

            if (commitExit != 0) {

                logger.warn(
                        "Nothing to commit or commit failed. Skipping push."
                );

                return false;
            }

            /*
             * Use Git's HTTP extra header so the token
             * is not placed directly inside the remote URL.
             */
            String authHeader =
                    "AUTHORIZATION: basic "
                            + java.util.Base64
                            .getEncoder()
                            .encodeToString(
                                    ("x-access-token:" + githubToken)
                                            .getBytes()
                            );

            int pushExit =
                    runGitCommand(
                            repoPath,
                            "-c",
                            "http.extraheader=" + authHeader,
                            "push",
                            cloneUrl,
                            "HEAD:" + branch
                    );

            if (pushExit == 0) {

                logger.info(
                        "Git push completed successfully."
                );

                return true;
            }

            logger.error(
                    "Git push failed with exit code {}",
                    pushExit
            );

            return false;

        } catch (Exception e) {

            logger.error(
                    "Error during commit/push",
                    e
            );

            return false;
        }
    }

    private int runGitCommand(
            Path workingDir,
            String... args) throws Exception {

        String[] fullCommand =
                new String[args.length + 1];

        fullCommand[0] = "git";

        System.arraycopy(
                args,
                0,
                fullCommand,
                1,
                args.length
        );

        ProcessBuilder pb =
                new ProcessBuilder(fullCommand);

        pb.directory(
                workingDir.toFile()
        );

        pb.redirectErrorStream(true);

        Process process =
                pb.start();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        process.getInputStream()
                                )
                        )
        ) {

            String line;

            while (
                    (line = reader.readLine()) != null
            ) {

                /*
                 * Do not print credentials or authentication
                 * headers to the console.
                 */
                logger.info("[git] {}", line);
            }
        }

        return process.waitFor();
    }
}

