package com.gauri.self_healing_devops_agent.service;

import com.gauri.self_healing_devops_agent.model.TestFailure;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TestFailureParserService {

    // Example:
    // com.gauri.patient_app.PatientAppApplicationTests.additionShouldWork
    // -- Time elapsed: 0.125 s <<< FAILURE!
    private static final Pattern FAILURE_PATTERN = Pattern.compile(
            "([\\w.]+)\\.(\\w+)\\s+--\\s+Time elapsed:.*<<<\\s+FAILURE!"
    );

    // Example:
    // org.opentest4j.AssertionFailedError: expected: <5> but was: <1>
    private static final Pattern ERROR_PATTERN = Pattern.compile(
            "^([\\w.$]+(?:Error|Exception)):\\s*(.*)$"
    );

    // Example:
    // at com.gauri.patient_app.PatientAppApplicationTests.additionShouldWork(PatientAppApplicationTests.java:17)
    private static final Pattern LOCATION_PATTERN = Pattern.compile(
            "at\\s+[\\w.$]+\\(([\\w]+\\.java):(\\d+)\\)"
    );

    public List<TestFailure> parse(String mavenOutput) {

        List<TestFailure> failures = new ArrayList<>();

        String[] lines = mavenOutput.split("\\R");

        for (int i = 0; i < lines.length; i++) {

            Matcher failureMatcher =
                    FAILURE_PATTERN.matcher(lines[i]);

            if (!failureMatcher.find()) {
                continue;
            }

            String fullClassPath = failureMatcher.group(1);
            String testMethod = failureMatcher.group(2);

            String testClass =
                    fullClassPath.substring(
                            fullClassPath.lastIndexOf('.') + 1
                    );

            String errorType = "";
            String errorMessage = "";
            String failingFile = "";
            int failingLine = -1;

            StringBuilder rawTrace = new StringBuilder();

            // Look at the next 20 lines for details
            for (
                    int j = i + 1;
                    j < Math.min(i + 20, lines.length);
                    j++
            ) {

                String line = lines[j];

                rawTrace
                        .append(line)
                        .append(System.lineSeparator());

                // Find error type and message
                Matcher errorMatcher =
                        ERROR_PATTERN.matcher(line.trim());

                if (
                        errorMatcher.find()
                                && errorType.isEmpty()
                ) {

                    errorType =
                            errorMatcher.group(1);

                    errorMessage =
                            errorMatcher.group(2);
                }

                // Find failing Java file and line
                Matcher locationMatcher =
                        LOCATION_PATTERN.matcher(line);

                if (
                        locationMatcher.find()
                                && failingFile.isEmpty()
                ) {

                    failingFile =
                            locationMatcher.group(1);

                    failingLine =
                            Integer.parseInt(
                                    locationMatcher.group(2)
                            );
                }
            }

            failures.add(
                    new TestFailure(
                            testClass,
                            testMethod,
                            errorType,
                            errorMessage,
                            failingFile,
                            failingLine,
                            rawTrace.toString()
                    )
            );
        }

        return failures;
    }
}