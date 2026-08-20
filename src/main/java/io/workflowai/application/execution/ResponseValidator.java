package io.workflowai.application.execution;

import io.workflowai.domain.workflow.response.ResponseContract;
import io.workflowai.domain.workflow.response.ResponseFormat;
import io.workflowai.domain.workflow.response.ValidationResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class ResponseValidator {

    private final JsonMapper jsonMapper;

    public ResponseValidator(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public ValidationResult validate(ResponseContract contract, String response) {
        if (response == null || response.isBlank()) {
            return ValidationResult.invalid("Response is empty");
        }
        if (contract.minLength() > 0 && response.length() < contract.minLength()) {
            return ValidationResult.invalid(
                    "Response is shorter than the required minimum of %d characters".formatted(contract.minLength()));
        }
        if (contract.format() == ResponseFormat.JSON) {
            return validateJson(contract, response);
        }
        return ValidationResult.valid("");
    }

    private ValidationResult validateJson(ResponseContract contract, String response) {
        JsonNode node;
        try {
            node = jsonMapper.readTree(response);
        } catch (RuntimeException ex) {
            return ValidationResult.invalid("Response is not a valid JSON object: %s".formatted(ex.getMessage()));
        }
        for (String field : contract.requiredFields()) {
            if (!node.has(field)) {
                return ValidationResult.invalid("Response is missing required field '%s'".formatted(field));
            }
        }
        return ValidationResult.valid("");
    }
}