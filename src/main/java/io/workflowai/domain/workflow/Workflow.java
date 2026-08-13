package io.workflowai.domain.workflow;

import io.workflowai.domain.agent.AgentProperties;
import io.workflowai.domain.agent.TriggerSource;
import io.workflowai.domain.exceptions.WorkflowExecutionException;
import io.workflowai.domain.task.SchedulingIntentDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Workflow {

    private static final Logger log = LoggerFactory.getLogger(Workflow.class);

    private final AgentProperties agentProperties;
    private final WorkflowExecutor executor;

    public Workflow(AgentProperties agentProperties, WorkflowExecutor workflowExecutor) {
        this.agentProperties = agentProperties;
        this.executor = workflowExecutor;
        log.debug("Workflow initialised for agent [{}]", agentProperties.id());
    }

    public void execute(UUID runId, UUID conversationId, TriggerSource triggerSource, String message) {
        log.debug("Starting workflow execution for agent [{}], conversation [{}], run [{}], model: [{}]",
                agentProperties.id(), conversationId, runId, agentProperties.model());

        Optional<String> scheduleCommand = SchedulingIntentDetector.extractCommand(message);

        Map<String, Object> initialState = new ConcurrentHashMap<>();
        initialState.put(WorkflowState.KEY_RUN_ID, runId);
        initialState.put(WorkflowState.KEY_CONVERSATION_ID, conversationId);
        initialState.put(WorkflowState.KEY_TRIGGER_SOURCE, triggerSource);
        initialState.put(WorkflowState.KEY_USER_MESSAGE, scheduleCommand.orElse(message));
        initialState.put(WorkflowState.KEY_SYSTEM_PROMPT, agentProperties.systemPrompt());
        initialState.put(WorkflowState.KEY_AGENT_PROPERTIES, agentProperties);
        initialState.put(WorkflowState.KEY_SCHEDULING_REQUESTED, scheduleCommand.isPresent());

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