package io.workflowai.application.agents;

import io.workflowai.application.pipeline.DefaultStageLabelProvider;
import io.workflowai.application.pipeline.WorkflowPipeline;
import io.workflowai.domain.model.AgentRequest;
import io.workflowai.domain.workflow.PipelineEvent;
import io.workflowai.domain.workflow.WorkflowPolicy;
import io.workflowai.domain.agents.Agent;
import io.workflowai.domain.model.AgentConfig;
import io.workflowai.ports.outbound.AgentMemoryStoragePort;
import io.workflowai.ports.outbound.LlmProviderPort;
import io.workflowai.ports.outbound.MessageStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.util.function.Consumer;

public abstract class BaseAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(BaseAgent.class);

    private final AgentConfig config;
    private final WorkflowPipeline pipeline;

    protected BaseAgent(
            AgentConfig config,
            LlmProviderPort llmProvider,
            MessageStoragePort messageStoragePort,
            AgentMemoryStoragePort agentMemoryStoragePort,
            WorkflowPolicy policy,
            JsonMapper jsonMapper) {
        this.config = config;
        this.pipeline = new WorkflowPipeline(
                config, llmProvider, messageStoragePort, agentMemoryStoragePort,
                policy, new DefaultStageLabelProvider(), jsonMapper);
        log.info("Agent [{}] initialised with provider [{}] and policyConfig [{}]", config.id(), config.provider(), policy);
    }

    @Override
    public AgentConfig getConfig() {
        return config;
    }

    @Override
    public void execute(AgentRequest request, Consumer<PipelineEvent> eventConsumer) {
        log.info("Agent [{}] executing request for conversation [{}]", config.id(), request.conversationId());
        pipeline.execute(request, eventConsumer);
    }
}
