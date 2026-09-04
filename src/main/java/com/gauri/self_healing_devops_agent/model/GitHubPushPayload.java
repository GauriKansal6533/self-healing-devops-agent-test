
        package com.gauri.self_healing_devops_agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubPushPayload {

    private String ref;

    private Repository repository;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Repository {

        @JsonProperty("clone_url")
        private String cloneUrl;

        @JsonProperty("full_name")
        private String fullName;
    }
}

