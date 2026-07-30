package io.workflowai.application.execution.stage;

import io.workflowai.domain.workflow.WorkflowStage;
import io.workflowai.domain.workflow.WorkflowState;
import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static io.workflowai.application.execution.workflow.WorkflowPrompts.refusalPrompt;

public class GenerateRefusalStage implements WorkflowStage {

    private static final Logger log = LoggerFactory.getLogger(GenerateRefusalStage.class);

    private final DecisionResponseGenerator decisionResponseGenerator;
    private final PersistResponseStage persistResponseStage;
    private final List<WorkflowEventStreamer> workflowEventStreamers;

    public GenerateRefusalStage(DecisionResponseGenerator decisionResponseGenerator, PersistResponseStage persistResponseStage,
                                 List<WorkflowEventStreamer> workflowEventStreamers) {
        this.decisionResponseGenerator = decisionResponseGenerator;
        this.persistResponseStage = persistResponseStage;
        this.workflowEventStreamers = workflowEventStreamers;
    }

    @Override
    public StageId stageId() {
        return StageId.GENERATE_REFUSAL;
    }

    @Override
    public Map<String, Object> execute(WorkflowState state) {
        workflowEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.GENERATE_REFUSAL));

        RoutingDecision decision = state.routingDecision()
                .orElse(RoutingDecision.refuse("Refusing request", state.userMessage()));
        String refusal = decisionResponseGenerator.generate(state, StageId.GENERATE_REFUSAL,
                refusalPrompt(state.systemPrompt(), state.agentProperties().workflowPolicyProperties(), decision));

        workflowEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.GENERATE_REFUSAL));
        String finalResponse = persistResponseStage.finalizeResponse(state, refusal);
        log.debug("[{}] Refusal response sent", state.agentProperties().id());

        return Map.of(WorkflowState.KEY_GENERATED_RESPONSE, finalResponse);
    }
}