package io.workflowai.domain.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.List;

public record PolicyConfig(
        List<String> capabilities,
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<String> greetings,
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<String> refuseMessages,
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<String> redirectMessages,
        int maxRetries) {
}