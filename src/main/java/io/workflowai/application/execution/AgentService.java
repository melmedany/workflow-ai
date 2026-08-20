package io.workflowai.application.execution;

import io.workflowai.application.execution.workflow.WorkflowFactory;
import io.workflowai.application.port.in.AgentUseCase;
import io.workflowai.application.port.in.ConversationUseCase;
import io.workflowai.application.port.out.AgentDefinitionStorage;
import io.workflowai.application.port.out.AgentRunTracker;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import io.workflowai.domain.agent.AgentDefinition;
import io.workflowai.domain.agent.AgentProperties;
import io.workflowai.domain.exceptions.AgentNotEnabledException;
import io.workflowai.domain.workflow.Workflow;
import io.workflowai.domain.workflow.WorkflowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class AgentService implements AgentUseCase {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentDefinitionStorage definitionStoragePort;
    private final WorkflowFactory workflowFactory;
    private final AgentRunTracker agentRunTracker;
    private final WorkflowEventStreamer workflowEventStreamer;
    private final ConversationUseCase conversationService;

    private final Map<UUID, Agent> agentsMap = new ConcurrentHashMap<>();

    public AgentService(
            AgentDefinitionStorage definitionStoragePort,
            WorkflowFactory workflowFactory,
            AgentRunTracker agentRunTracker,
            WorkflowEventStreamer workflowEventStreamer,
            ConversationUseCase conversationService) {
        this.definitionStoragePort = definitionStoragePort;
        this.workflowFactory = workflowFactory;
        this.agentRunTracker = agentRunTracker;
        this.workflowEventStreamer = workflowEventStreamer;
        this.conversationService = conversationService;

        this.definitionStoragePort.findAll()
                .forEach(agentDefinition -> this.reload(agentDefinition.agentId()));
    }

    @Override
    public UUID trigger(AgentRequest request, Consumer<WorkflowEvent> eventConsumer) {
        Agent agent = getAgent(request.agentId());

        UUID runId = agentRunTracker.start(request.triggerSource(), agent.properties().id(), request.conversationId(), request.taskId());
        workflowEventStreamer.registerConsumer(runId, eventConsumer);

        try {
            agent.execute(runId, request);
            agentRunTracker.complete(runId);
        } catch (RuntimeException ex) {
            agentRunTracker.fail(runId, ex.getMessage());
            throw ex;
        } finally {
            workflowEventStreamer.revokeConsumer(runId);
        }
        return runId;
    }

    @Override
    public Agent getEnabledAgent(UUID agentId) {
        return getAgent(agentId);
    }

    @Override
    public List<Agent> getEnabledAgents() {
        return new ArrayList<>(agentsMap.values());
    }

    @Override
    public void reload(UUID agentId) {
        AgentDefinition agentDef = definitionStoragePort.findById(agentId);

        if (!agentDef.details().enabled()) {
            agentsMap.remove(agentId);
            return;
        }

        createAgent(agentDef);
    }

    @Override
    public void remove(UUID agentId) {
        conversationService.getConversationsForAgent(agentId)
                .forEach(conv -> conversationService.deleteConversation(conv.agentId(), conv.id()));
        agentsMap.remove(agentId);
    }

    @Override
    public String workflowDiagram(UUID agentId) {
        return getAgent(agentId).workflowDiagram();
    }

    private Agent getAgent(UUID agentId) {
        Agent agent = agentsMap.get(agentId);
        if (agent == null) {
            AgentDefinition agentDef = definitionStoragePort.findById(agentId);

            if (!agentDef.details().enabled()) throw new AgentNotEnabledException(agentId);

            return createAgent(agentDef);
        }
        return agentsMap.get(agentId);
    }

    private Agent createAgent(AgentDefinition definition) {
        AgentProperties agentProperties = toAgentProperties(definition);
        Workflow workflow = workflowFactory.build(agentProperties.workflowId(), agentProperties);

        log.debug("Initializing agent [{}] with chat provider [{}] and workflowPolicy [{}]",
                agentProperties.displayName(), agentProperties.chatProviderId(), agentProperties.workflowPolicy());

        Agent agent = new DefaultAgent(agentProperties, workflow);

        agentsMap.put(agent.properties().id(), agent);

        return agent;
    }

    private AgentProperties toAgentProperties(AgentDefinition definition) {
        return new AgentProperties(
                definition.agentId(),
                definition.details().displayName(),
                definition.details().description(),
                definition.details().enabled(),
                definition.workflowId(),
                definition.chatProperties().providerId(),
                definition.chatProperties().model(),
                definition.chatProperties().temperature(),
                definition.chatProperties().agentPrompt(),
                definition.chatProperties().memoryEnabled(),
                definition.workflowPolicy());
    }

    private record DefaultAgent(AgentProperties properties, Workflow workflow) implements Agent {

        @Override
        public AgentProperties properties() {
            return properties;
        }

        @Override
        public List<String> tags() {
            return properties.workflowPolicy().supportedCapabilities();
        }

        @Override
        public String workflowDiagram() {
            return workflow.diagram("%s Workflow Diagram".formatted(properties.workflowId().name()));
        }

        @Override
        public void execute(UUID runId, AgentRequest request) {
            log.debug("Agent [{}] executing request for conversation [{}]", properties.id(), request.conversationId());
            workflow.execute(runId, request.conversationId(), request.triggerSource(), request.message());
        }
    }
}