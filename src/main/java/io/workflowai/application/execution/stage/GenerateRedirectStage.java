package io.workflowai.application.execution.stage;

import io.workflowai.application.execution.WorkflowState;
import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static io.workflowai.application.execution.WorkflowPrompts.redirectPrompt;

public class GenerateRedirectStage implements WorkflowStage {

    private static final Logger log = LoggerFactory.getLogger(GenerateRedirectStage.class);

    private final DecisionResponseGenerator decisionResponseGenerator;
    private final PersistResponseStage persistResponseStage;
    private final List<WorkflowEventStreamer> workflowEventStreamers;

    public GenerateRedirectStage(DecisionResponseGenerator decisionResponseGenerator, PersistResponseStage persistResponseStage,
                                  List<WorkflowEventStreamer> workflowEventStreamers) {
        this.decisionResponseGenerator = decisionResponseGenerator;
        this.persistResponseStage = persistResponseStage;
        this.workflowEventStreamers = workflowEventStreamers;
    }

    @Override
    public StageId stageId() {
        return StageId.GENERATE_REDIRECT;
    }

    @Override
    public Map<String, Object> execute(WorkflowState state) {
        workflowEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.GENERATE_REDIRECT));

        RoutingDecision decision = state.routingDecision()
                .orElse(RoutingDecision.redirect("Redirecting mixed-scope request", state.userMessage()));
        String redirect = decisionResponseGenerator.generate(state, StageId.GENERATE_REDIRECT,
                redirectPrompt(state.systemPrompt(), state.agentProperties().workflowPolicyProperties(), decision));

        workflowEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.GENERATE_REDIRECT));
        String finalResponse = persistResponseStage.finalizeResponse(state, redirect);
        log.debug("[{}] Redirect response sent", state.agentProperties().id());

        return Map.of(WorkflowState.KEY_GENERATED_RESPONSE, finalResponse);
    }
}