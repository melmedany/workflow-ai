package io.workflowai.application.execution.workflow;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.execution.stage.StageSettings;
import io.workflowai.domain.agent.AgentProperties;
import io.workflowai.domain.workflow.Workflow;
import io.workflowai.domain.workflow.WorkflowExecutor;
import io.workflowai.domain.workflow.WorkflowExecutorFactory;
import io.workflowai.domain.workflow.WorkflowId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkflowFactory {

    private static final Logger log = LoggerFactory.getLogger(WorkflowFactory.class);

    private final StageSettings stagesProperties;
    private final ChatProviderRegistry chatProviderRegistry;
    private final WorkflowExecutorFactory workflowExecutorFactory;

    public WorkflowFactory(
            StageSettings stagesProperties,
            ChatProviderRegistry chatProviderRegistry,
            WorkflowExecutorFactory workflowExecutorFactory) {
        this.stagesProperties = stagesProperties;
        this.chatProviderRegistry = chatProviderRegistry;
        this.workflowExecutorFactory = workflowExecutorFactory;
    }

    public Workflow build(WorkflowId workflowId, AgentProperties agentProperties) {
        log.debug("Building [{}] workflow for agent [{}]", workflowId, agentProperties.id());
        chatProviderRegistry.validate(agentProperties.chatProviderId(), agentProperties.model());
        stagesProperties.stages().forEach(stage ->
                chatProviderRegistry.validate(stage.chatProviderId(), stage.model()));

        WorkflowExecutor workflowExecutor = workflowExecutorFactory.build(workflowId);
        return new Workflow(agentProperties, workflowExecutor);
    }
}