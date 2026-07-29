package io.workflowai.application.execution.stage;

import io.workflowai.application.execution.WorkflowState;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.application.port.out.AgentMemoryStorage;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class LoadMemoryStage implements WorkflowStage {

    private static final Logger log = LoggerFactory.getLogger(LoadMemoryStage.class);

    private final AgentMemoryStorage agentMemoryStorage;
    private final List<WorkflowEventStreamer> workflowEventStreamers;

    public LoadMemoryStage(AgentMemoryStorage agentMemoryStorage, List<WorkflowEventStreamer> workflowEventStreamers) {
        this.agentMemoryStorage = agentMemoryStorage;
        this.workflowEventStreamers = workflowEventStreamers;
    }

    @Override
    public StageId stageId() {
        return StageId.LOAD_MEMORY;
    }

    @Override
    public Map<String, Object> execute(WorkflowState state) {
        workflowEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.LOAD_MEMORY));

        String memoryContext = "";
        if (state.agentProperties().memoryEnabled()) {
            memoryContext = agentMemoryStorage
                    .getMemory(state.conversationId(), state.agentProperties().id())
                    .orElse("");
            log.debug("[{}] Loaded compact memory ({} chars)", state.agentProperties().id(), memoryContext.length());
        }

        workflowEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.LOAD_MEMORY));
        return Map.of(WorkflowState.KEY_MEMORY_CONTEXT, memoryContext);
    }
}