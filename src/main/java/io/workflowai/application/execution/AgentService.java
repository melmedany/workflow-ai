package io.workflowai.application.execution;

import io.workflowai.application.execution.workflow.WorkflowFactory;
import io.workflowai.application.port.in.AgentUseCase;
import io.workflowai.application.port.out.AgentDefinitionStorage;
import io.workflowai.application.port.out.AgentRunTracker;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import io.workflowai.domain.agent.AgentDefinition;
import io.workflowai.domain.agent.AgentProperties;
import io.workflowai.domain.exceptions.AgentNotFoundException;
import io.workflowai.domain.workflow.Workflow;
import io.workflowai.domain.workflow.WorkflowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class AgentService implements AgentUseCase {

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
    public UUID trigger(AgentRequest request, Consumer<WorkflowEvent> eventConsumer) {
        Agent agent = get(request.agentId());

        UUID runId = agentRunTracker.start(request.triggerSource(), agent.properties().id(), request.conversationId(), request.taskId());
        workflowEventStreamer.registerConsumer(runId, eventConsumer);

        try {
            agent.execute(runId, request);
            agentRunTracker.complete(runId);
        } catch (Exception ex) {
            // TODO: handle different workflow exceptions separately instead of one try/catch
            agentRunTracker.fail(runId, ex.getMessage());
            throw ex;
        } finally {
            workflowEventStreamer.revokeConsumer(runId);
        }
        return runId;
    }

    @Override
    public List<Agent> getEnabledAgents() {
        return definitionStoragePort.findEnabledAgents().stream()
                .map(this::createAgent)
                .toList();
    }

    /**
     * Created agents can be cached instead of creating them on every request. However, that will introduce extra complexity to keep cached agents up to date.
     * AgentDefinition can be versioned as well to avoid changing the agent in the middle of a request.
     */
    private Agent createAgent(AgentDefinition definition) {
        AgentProperties agentProperties = toAgentProperties(definition);
        Workflow workflow = workflowFactory.build(agentProperties.workflowId(), agentProperties);

        log.debug("Initialising agent [{}] with chat provider [{}] and workflowPolicy [{}]",
                agentProperties.displayName(), agentProperties.chatProviderId(), agentProperties.workflowPolicy());
        return new Agent() {
            @Override
            public AgentProperties properties() {
                return agentProperties;
            }

            @Override
            public List<String> tags() {
                return agentProperties.workflowPolicy().supportedCapabilities();
            }

            @Override
            public String workflowDiagram() {
                return workflow.diagram("%s Workflow Diagram".formatted(agentProperties.workflowId().name()));
            }

            @Override
            public void execute(UUID runId, AgentRequest request) {
                log.debug("Agent [{}] executing request for conversation [{}]", agentProperties.id(), request.conversationId());
                workflow.execute(runId, request.conversationId(), request.triggerSource(), request.message());
            }
        };
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
}