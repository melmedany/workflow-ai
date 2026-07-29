package io.workflowai.domain.workflow;

import io.workflowai.domain.workflow.response.ResponseContract;

import java.io.Serializable;
import java.util.List;

public record WorkflowPolicy(
        List<String> supportedCapabilities,
        ResponseContract responseContract,
        String fallbackFailedToProcess) implements Serializable {

    public WorkflowPolicy {
        if (responseContract == null) {
            responseContract = ResponseContract.text();
        }
    }

    public String failedToProcessMessage() {
        return fallbackFailedToProcess;
    }
}
