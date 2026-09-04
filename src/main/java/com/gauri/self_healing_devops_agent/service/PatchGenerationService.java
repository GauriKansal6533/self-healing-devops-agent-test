
package com.gauri.self_healing_devops_agent.service;

import com.gauri.self_healing_devops_agent.model.TestFailure;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PatchGenerationService {

    private static final Logger logger =
            LoggerFactory.getLogger(PatchGenerationService.class);

    @Value("${gemini.api.key}")
    private String apiKey;

    private ChatModel chatModel;

    @PostConstruct
    public void init() {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY is not configured"
            );
        }

        chatModel = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-3.5-flash-lite")
                .build();

        logger.info("Gemini ChatModel initialized successfully.");
    }

    /**
     * Sends the test failure and buggy source code to Gemini
     * and returns the proposed corrected source file.
     */
    public String generatePatch(
            TestFailure failure,
            String buggySourceCode
    ) {

        String prompt = buildPrompt(
                failure,
                buggySourceCode
        );

        logger.info(
                "Sending failed test information to Gemini..."
        );

        String response = chatModel.chat(prompt);

        logger.info(
                "Received proposed patch from Gemini."
        );

        return response;
    }

    private String buildPrompt(
            TestFailure failure,
            String buggySourceCode
    ) {

        return """
                You are an expert Java debugging assistant.

                A test has failed in a Java Maven project.

                FAILURE INFORMATION
                --------------------
                Test class: %s
                Test method: %s
                Error type: %s
                Error message: %s

                SOURCE CODE
                -----------
                The following is the complete Java source file
                that is believed to contain the bug:

                ```java
                %s
                ```

                TASK
                ----
                Analyze the failing test and source code.

                Determine the most likely bug and correct it.

                OUTPUT REQUIREMENTS
                -------------------
                Return ONLY the complete corrected Java source file.

                Do NOT provide:
                - explanations
                - comments outside the source code
                - markdown
                - ```java code fences
                - ``` code fences

                Your response will be written directly to a Java source file,
                so it must contain only valid Java source code.

                """.formatted(
                failure.testClass(),
                failure.testMethod(),
                failure.errorType(),
                failure.errorMessage(),
                buggySourceCode
        );
    }
}

