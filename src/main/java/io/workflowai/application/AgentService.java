package io.workflowai.application;

import io.workflowai.application.pipeline.WorkflowPipeline;
import io.workflowai.application.pipeline.WorkflowPipelineFactory;
import io.workflowai.application.pipeline.WorkflowPipelineId;
import io.workflowai.domain.agents.Agent;
import io.workflowai.domain.agents.AgentDefinition;
import io.workflowai.domain.exceptions.AgentNotFoundException;
import io.workflowai.domain.model.AgentProperties;
import io.workflowai.domain.model.AgentRequest;
import io.workflowai.domain.workflow.PipelineEvent;
import io.workflowai.ports.inbound.AgentProvider;
import io.workflowai.ports.outbound.AgentDefinitionStorage;
import io.workflowai.ports.outbound.AgentRunTracker;
import io.workflowai.ports.outbound.PipelineEventStreamer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Component
public class AgentService implements AgentProvider {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentDefinitionStorage definitionStoragePort;
    private final WorkflowPipelineFactory workflowPipelineFactory;
    private final AgentRunTracker agentRunTracker;
    private final PipelineEventStreamer pipelineEventStreamer;

    public AgentService(
            AgentDefinitionStorage definitionStoragePort,
            WorkflowPipelineFactory workflowPipelineFactory,
            AgentRunTracker agentRunTracker,
            PipelineEventStreamer pipelineEventStreamer) {
        this.definitionStoragePort = definitionStoragePort;
        this.workflowPipelineFactory = workflowPipelineFactory;
        this.agentRunTracker = agentRunTracker;
        this.pipelineEventStreamer = pipelineEventStreamer;
    }

    @Override
    public Agent get(UUID id) {
        return definitionStoragePort.findById(id)
                .map(this::createAgent)
                .orElseThrow(() -> new AgentNotFoundException(id));

    }

    @Override
    public void trigger(AgentRequest request, Consumer<PipelineEvent> eventConsumer) {
        Agent agent = get(request.agentId());

        UUID runId = agentRunTracker.start(request.triggerSource(), agent.properties().id(), request.conversationId());
        pipelineEventStreamer.registerConsumer(runId, eventConsumer);

        try {
            agent.execute(runId, request);
            agentRunTracker.complete(runId);
        } catch (Exception ex) {
            agentRunTracker.fail(runId, ex.getMessage());
            throw ex;
        } finally {
            pipelineEventStreamer.revokeConsumer(runId);
        }
    }

    @Override
    public List<Agent> getAll() {
        return definitionStoragePort.findAll().stream()
                .map(this::createAgent)
                .toList();
    }

    private Agent createAgent(AgentDefinition definition) {
        AgentProperties agentProperties = toAgentProperties(definition);
        WorkflowPipeline pipeline = workflowPipelineFactory.build(WorkflowPipelineId.STANDARD, agentProperties);

        log.debug("Initialising agent [{}] with llm provider [{}] and workflowPolicyProperties [{}]",
                agentProperties.displayName(), agentProperties.llmProviderId(), agentProperties.workflowPolicyProperties());
        return new Agent() {
            @Override
            public AgentProperties properties() {
                return agentProperties;
            }

            @Override
            public List<String> tags() {
                return agentProperties.workflowPolicyProperties().supportedCapabilities();
            }

            @Override
            public String workflowDiagram() {
                return pipeline.workflowDiagram("%s Workflow Diagram".formatted(WorkflowPipelineId.STANDARD.name()));
            }

            @Override
            public void execute(UUID runId, AgentRequest request) {
                log.debug("Agent [{}] executing request for conversation [{}]", agentProperties.id(), request.conversationId());
                pipeline.execute(runId, request);
            }
        };
    }

    private AgentProperties toAgentProperties(AgentDefinition definition) {
        return new AgentProperties(
                definition.agentId(),
                definition.details().displayName(),
                definition.details().description(),
                definition.details().enabled(),
                definition.llmProperties().providerId(),
                definition.llmProperties().model(),
                definition.llmProperties().temperature(),
                definition.llmProperties().agentPrompt(),
                definition.llmProperties().memoryEnabled(),
                definition.workflowPolicyProperties());
    }
}