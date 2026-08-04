package io.workflowai.application.execution.stage;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.domain.workflow.WorkflowStage;
import io.workflowai.domain.workflow.WorkflowState;
import io.workflowai.domain.exceptions.ClassificationException;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static io.workflowai.application.execution.workflow.WorkflowPrompts.CLASSIFICATION_SYSTEM_PROMPT;
import static io.workflowai.application.execution.workflow.WorkflowPrompts.classificationPrompt;

public class ClassificationStage implements WorkflowStage {

    private static final Logger log = LoggerFactory.getLogger(ClassificationStage.class);

    private final ChatProviderRegistry chatProviderRegistry;
    private final StageSettings stagesProperties;
    private final JsonMapper jsonMapper;
    private final List<WorkflowEventStreamer> workflowEventStreamers;

    public ClassificationStage(ChatProviderRegistry chatProviderRegistry, StageSettings stagesProperties,
                               JsonMapper jsonMapper, List<WorkflowEventStreamer> workflowEventStreamers) {
        this.chatProviderRegistry = chatProviderRegistry;
        this.stagesProperties = stagesProperties;
        this.jsonMapper = jsonMapper;
        this.workflowEventStreamers = workflowEventStreamers;
    }

    @Override
    public StageId stageId() {
        return StageId.CLASSIFICATION;
    }

    @Override
    public Map<String, Object> execute(WorkflowState state) {
        workflowEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.CLASSIFICATION));

        RoutingDecision decision = performClassification(state);
        workflowEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.CLASSIFICATION));
        workflowEventStreamers.forEach(s -> s.decisionMade(state.runId(), decision));

        log.debug("[{}] Classification result: {} — {}", state.agentProperties().id(), decision.decisionMode(), decision.reason());

        return Map.of(WorkflowState.KEY_ROUTING_DECISION, decision);
    }

    private RoutingDecision performClassification(WorkflowState state) {
        StageSettings.StageSetting stageProperties = stagesProperties.get(StageId.CLASSIFICATION);
        String prompt = classificationPrompt(state.agentProperties().id(), state.agentProperties().workflowPolicy(), state.userMessage());

        ChatCompletionRequest classifyRequest = new ChatCompletionRequest(stageProperties.model(), 0.1, CLASSIFICATION_SYSTEM_PROMPT, prompt, "");
        try {
            String jsonResponse = chatProviderRegistry.get(stageProperties.chatProviderId()).call(classifyRequest);
            return parseRoutingDecision(state, jsonResponse);
        } catch (ClassificationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("[{}] Classification call failed, defaulting to REFUSE: {}", state.agentProperties().id(), ex.getMessage());
            return RoutingDecision.refuse("Classification unavailable: " + ex.getMessage(), state.userMessage());
        }
    }

    private RoutingDecision parseRoutingDecision(WorkflowState state, String jsonResponse) {
        try {
            return jsonMapper.readValue(jsonResponse, RoutingDecision.class);
        } catch (Exception ex) {
            throw new ClassificationException(state.agentProperties().id(),
                    "Failed to parse routing decision from response: %s".formatted(jsonResponse), ex);
        }
    }
}