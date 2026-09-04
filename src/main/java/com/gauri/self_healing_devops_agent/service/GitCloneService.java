
        package com.gauri.self_healing_devops_agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

@Service
public class GitCloneService {

    private static final Logger logger =
            LoggerFactory.getLogger(GitCloneService.class);

    /**
     * Clone a GitHub repository into a temporary directory.
     *
     * @param cloneUrl GitHub repository clone URL
     * @param branch   branch to clone
     * @return path of the cloned repository
     */
    public Path cloneRepo(String cloneUrl, String branch)
            throws IOException, InterruptedException {

        // Create a unique temporary directory
        Path tempDir =
                Files.createTempDirectory("agent-clone-");

        logger.info(
                "Cloning repository: {}",
                cloneUrl
        );

        logger.info(
                "Branch: {}",
                branch
        );

        logger.info(
                "Temporary directory: {}",
                tempDir
        );

        ProcessBuilder processBuilder = new ProcessBuilder(
                "git",
                "clone",
                "--branch",
                branch,
                "--single-branch",
                "--depth",
                "1",
                cloneUrl,
                tempDir.toAbsolutePath().toString()
        );

        // Combine stdout and stderr
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        // Read Git output
        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        process.getInputStream()
                                )
                        )
        ) {

            String line;

            while ((line = reader.readLine()) != null) {

                logger.info(
                        "[git clone] {}",
                        line
                );
            }
        }

        // IMPORTANT:
        // Wait until Git completely finishes
        int exitCode = process.waitFor();

        if (exitCode != 0) {

            logger.error(
                    "Git clone failed with exit code: {}",
                    exitCode
            );

            // Remove incomplete clone
            cleanup(tempDir);

            throw new IOException(
                    "git clone failed with exit code "
                            + exitCode
            );
        }

        logger.info(
                "Repository cloned successfully: {}",
                tempDir
        );

        return tempDir;
    }

    /**
     * Delete the temporary cloned repository.
     *
     * Cleanup is performed recursively.
     */
    public void cleanup(Path repoPath) {

        if (repoPath == null) {

            logger.info(
                    "Cleanup skipped because repository path is null."
            );

            return;
        }

        if (!Files.exists(repoPath)) {

            logger.info(
                    "Cleanup skipped because directory does not exist: {}",
                    repoPath
            );

            return;
        }

        logger.info(
                "Starting cleanup: {}",
                repoPath
        );

        try {

            /*
             * Walk through the entire repository.
             *
             * reverseOrder() ensures that files/directories
             * inside the repository are deleted before their
             * parent directories.
             */
            Files.walk(repoPath)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {

                        try {

                            /*
                             * Windows can sometimes mark files
                             * as read-only, especially files inside
                             * the .git directory.
                             */
                            if (Files.isRegularFile(path)) {

                                path.toFile().setWritable(true);
                            }

                            Files.deleteIfExists(path);

                            logger.debug(
                                    "Deleted: {}",
                                    path
                            );

                        } catch (IOException e) {

                            logger.warn(
                                    "Could not delete: {}",
                                    path,
                                    e
                            );
                        }
                    });

            /*
             * Verify that the root directory was actually deleted.
             */
            if (Files.exists(repoPath)) {

                logger.warn(
                        "Cleanup incomplete. Directory still exists: {}",
                        repoPath
                );

            } else {

                logger.info(
                        "Cleanup successful: {}",
                        repoPath
                );
            }

        } catch (IOException e) {

            logger.error(
                    "Failed while walking repository for cleanup: {}",
                    repoPath,
                    e
            );
        }
    }
}

