package com.gauri.self_healing_devops_agent.model;

public record TestFailure(
        String testClass,
        String testMethod,
        String errorType,
        String errorMessage,
        String failingFile,
        int failingLine,
        String rawStackTrace
) {}