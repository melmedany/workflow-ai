package io.workflowai.application.execution;

import io.workflowai.domain.workflow.WorkflowId;
import io.workflowai.domain.agent.AgentDefinition;
import io.workflowai.domain.exceptions.AgentNotFoundException;
import io.workflowai.domain.agent.AgentProperties;
import io.workflowai.domain.workflow.WorkflowEvent;
import io.workflowai.application.port.in.AgentProvider;
import io.workflowai.application.port.out.AgentDefinitionStorage;
import io.workflowai.application.port.out.AgentRunTracker;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class AgentService implements AgentProvider {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentDefinitionStorage definitionStoragePort;
    private final WorkflowFactory workflowFactory;
    private final AgentRunTracker agentRunTracker;
    private final WorkflowEventStreamer workflowEventStreamer;

    public AgentService(
            AgentDefinitionStorage definitionStoragePort,
            WorkflowFactory workflowFactory,
            AgentRunTracker agentRunTracker,
            WorkflowEventStreamer workflowEventStreamer) {
        this.definitionStoragePort = definitionStoragePort;
        this.workflowFactory = workflowFactory;
        this.agentRunTracker = agentRunTracker;
        this.workflowEventStreamer = workflowEventStreamer;
    }

    @Override
    public Agent get(UUID id) {
        return definitionStoragePort.findById(id)
                .map(this::createAgent)
                .orElseThrow(() -> new AgentNotFoundException(id));

    }

    @Override
    public void trigger(AgentRequest request, Consumer<WorkflowEvent> eventConsumer) {
        Agent agent = get(request.agentId());

        UUID runId = agentRunTracker.start(request.triggerSource(), agent.properties().id(), request.conversationId());
        workflowEventStreamer.registerConsumer(runId, eventConsumer);

        try {
            agent.execute(runId, request);
            agentRunTracker.complete(runId);
        } catch (Exception ex) {
            // TODO: better handle workflow exceptions
            agentRunTracker.fail(runId, ex.getMessage());
            throw ex;
        } finally {
            workflowEventStreamer.revokeConsumer(runId);
        }
    }

    @Override
    public String workflowDiagram(UUID id) {
        return get(id).workflowDiagram();
    }

    @Override
    public List<Agent> getAll() {
        return definitionStoragePort.findAll().stream()
                .map(this::createAgent)
                .toList();
    }

    // TODO: created agents can be cached instead of creating them on every request
    private Agent createAgent(AgentDefinition definition) {
        AgentProperties agentProperties = toAgentProperties(definition);
        Workflow workflow = workflowFactory.build(WorkflowId.STANDARD, agentProperties);

        log.debug("Initialising agent [{}] with chat provider [{}] and workflowPolicyProperties [{}]",
                agentProperties.displayName(), agentProperties.chatProviderId(), agentProperties.workflowPolicyProperties());
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
                return workflow.diagram("%s Workflow Diagram".formatted(WorkflowId.STANDARD.name()));
            }

            @Override
            public void execute(UUID runId, AgentRequest request) {
                log.debug("Agent [{}] executing request for conversation [{}]", agentProperties.id(), request.conversationId());
                workflow.execute(runId, request);
            }
        };
    }

    private AgentProperties toAgentProperties(AgentDefinition definition) {
        return new AgentProperties(
                definition.agentId(),
                definition.details().displayName(),
                definition.details().description(),
                definition.details().enabled(),
                definition.chatProperties().providerId(),
                definition.chatProperties().model(),
                definition.chatProperties().temperature(),
                definition.chatProperties().agentPrompt(),
                definition.chatProperties().memoryEnabled(),
                definition.workflowPolicyProperties());
    }
}