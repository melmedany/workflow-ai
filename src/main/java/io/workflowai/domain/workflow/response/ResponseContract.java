package io.workflowai.domain.workflow.response;

import java.util.List;

public record ResponseContract(
        ResponseFormat format,
        List<String> requiredFields,
        int minLength) {

    public ResponseContract {
        if (format == null) {
            format = ResponseFormat.TEXT;
        }
        if (requiredFields == null) {
            requiredFields = List.of();
        }
    }

    public static ResponseContract text() {
        return new ResponseContract(ResponseFormat.TEXT, List.of(), 0);
    }
}
