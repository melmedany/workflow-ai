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
import io.workflowai.ports.outbound.RunHistoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AgentService implements AgentProvider {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentDefinitionStorage definitionStoragePort;
    private final WorkflowPipelineFactory workflowPipelineFactory;
    private final RunHistoryPort runHistoryPort;
    // TODO: cache can have bad performance as agents grows
    private final Map<UUID, Agent> agentsCache;

    public AgentService(
            AgentDefinitionStorage definitionStoragePort,
            WorkflowPipelineFactory workflowPipelineFactory,
            RunHistoryPort runHistoryPort) {
        this.definitionStoragePort = definitionStoragePort;
        this.workflowPipelineFactory = workflowPipelineFactory;
        this.runHistoryPort = runHistoryPort;
        this.agentsCache = buildAgentsCache();
    }

    @Override
    public Agent get(UUID id) {
        Agent agent = agentsCache.get(id);
        if (agent == null) {
            agent = definitionStoragePort.findById(id)
                    .map(this::createAgent)
                    .orElseThrow(() -> new AgentNotFoundException(id));
            agentsCache.put(id, agent);
        }
        return agent;
    }

    @Override
    public void reload(UUID id) {
        agentsCache.remove(id);
        Agent agent = definitionStoragePort.findById(id)
                .map(this::createAgent)
                .orElseThrow(() -> new AgentNotFoundException(id));
        agentsCache.put(id, agent);
    }

    @Override
    public void trigger(AgentRequest request, Consumer<PipelineEvent> eventConsumer) {
        Agent agent = get(request.agentId());

        UUID runId = runHistoryPort.start(request.triggerSource(), agent.properties().id(), request.conversationId());
        agent.execute(runId, request, eventConsumer);

        // TODO extract start, complete and fail logic from WorkflowPipeline to here
    }

    @Override
    public List<Agent> getAll() {
        return agentsCache.values().stream().toList();
    }

    private Map<UUID, Agent> buildAgentsCache() {
        List<Agent> agents = definitionStoragePort.findAll().stream()
                .map(this::createAgent)
                .toList();
        return agents.stream().collect(Collectors.toConcurrentMap(a -> a.properties().id(), Function.identity()));
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
            public void execute(UUID runId, AgentRequest request, Consumer<PipelineEvent> eventConsumer) {
                log.debug("Agent [{}] executing request for conversation [{}]", agentProperties.id(), request.conversationId());
                pipeline.execute(runId, request, eventConsumer);
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