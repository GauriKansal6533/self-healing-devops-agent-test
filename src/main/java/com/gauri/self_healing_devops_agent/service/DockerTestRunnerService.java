package com.gauri.self_healing_devops_agent.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;

@Service
public class DockerTestRunnerService {

    private static final Logger logger =
            LoggerFactory.getLogger(DockerTestRunnerService.class);

    public TestResult runTests(Path repoPath) {

        StringBuilder output = new StringBuilder();
        boolean success;

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "run", "--rm",
                    "-v", repoPath.toAbsolutePath() + ":/app",
                    "-w", "/app",
                    "maven:3.9-eclipse-temurin-21",
                    "mvn", "test"
            );

            pb.redirectErrorStream(true);

            Process process = pb.start();

            try (BufferedReader reader =
                         new BufferedReader(
                                 new InputStreamReader(process.getInputStream()))) {

                String line;

                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.info(line);
                }
            }

            int exitCode = process.waitFor();

            success = (exitCode == 0);

        } catch (Exception e) {

            logger.error("Error running Docker test container", e);

            output.append("Exception: ")
                    .append(e.getMessage());

            success = false;
        }

        return new TestResult(success, output.toString());
    }

    public record TestResult(boolean success, String logOutput) {
    }
}