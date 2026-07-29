package io.workflowai.application.execution;

import io.workflowai.domain.exceptions.WorkflowExecutionException;
import io.workflowai.domain.agent.AgentProperties;
import io.workflowai.domain.run.WorkflowExecutionResult;
import io.workflowai.application.port.out.WorkflowExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

public class Workflow {

    private static final Logger log = LoggerFactory.getLogger(Workflow.class);

    private final AgentProperties agentProperties;
    private final WorkflowExecutor executor;

    public Workflow(AgentProperties agentProperties, WorkflowExecutor workflowExecutor) {
        this.agentProperties = agentProperties;
        this.executor = workflowExecutor;
        log.debug("Workflow initialised for agent [{}]", agentProperties.id());
    }

    public void execute(UUID runId, AgentRequest request) {
        UUID conversationId = request.conversationId();
        log.debug("Starting workflow execution for agent [{}], conversation [{}], run [{}], model: [{}]",
                agentProperties.id(), conversationId, runId, agentProperties.model());

        Map<String, Object> initialState = Map.of(
                WorkflowState.KEY_RUN_ID, runId,
                WorkflowState.KEY_CONVERSATION_ID, conversationId,
                WorkflowState.KEY_USER_MESSAGE, request.message(),
                WorkflowState.KEY_TRIGGER_SOURCE, request.triggerSource(),
                WorkflowState.KEY_SYSTEM_PROMPT, agentProperties.systemPrompt(),
                WorkflowState.KEY_AGENT_PROPERTIES, agentProperties
        );

        WorkflowExecutionResult result = executor.execute(initialState);
        switch (result.outcome()) {
            case COMPLETED -> log.debug("Workflow completed for agent [{}], conversation [{}], run [{}]",
                    agentProperties.id(), conversationId, runId);
            case TIMED_OUT, FAILED -> throw new WorkflowExecutionException(agentProperties.id(), result.message(), result.cause());
        }
    }

    public String diagram(String title) {
        return executor.diagram(title);
    }
}