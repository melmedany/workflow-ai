package io.workflowai.application.execution.stage;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.execution.workflow.WorkflowPrompts;
import io.workflowai.application.port.out.AgentMemoryStorage;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.domain.agent.AgentProperties;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.WorkflowStage;
import io.workflowai.domain.workflow.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

public class CompactMemoryStage implements WorkflowStage {

    private static final Logger log = LoggerFactory.getLogger(CompactMemoryStage.class);

    private final ChatProviderRegistry chatProviderRegistry;
    private final StageSettings stagesProperties;
    private final AgentMemoryStorage agentMemoryStorage;

    public CompactMemoryStage(ChatProviderRegistry chatProviderRegistry, StageSettings stagesProperties,
                              AgentMemoryStorage agentMemoryStorage) {
        this.chatProviderRegistry = chatProviderRegistry;
        this.stagesProperties = stagesProperties;
        this.agentMemoryStorage = agentMemoryStorage;
    }

    @Override
    public StageId stageId() {
        return StageId.COMPACT_MEMORY;
    }

    @Override
    public Map<String, Object> execute(WorkflowState state) {
        AgentProperties agentProperties = state.agentProperties();
        if (!agentProperties.memoryEnabled()) {
            return Map.of();
        }

        String previousMemory = state.memoryContext();
        String userMessage = state.userMessage();
        String response = state.generatedResponse().orElse("");

        compactMemory(agentProperties.id(), agentProperties.systemPrompt(), state.conversationId(), previousMemory, userMessage, response);
        return Map.of();
    }

    private void compactMemory(UUID agentId, String agentSystemPrompt, UUID conversationId,
                                     String previousMemory, String userMessage, String response) {
        if (response.isBlank()) {
            return;
        }

        StageSettings.StageSetting stageProperties = stagesProperties.get(StageId.COMPACT_MEMORY);

        try {
            String prompt = WorkflowPrompts.memoryCompactionPrompt(previousMemory, userMessage, response);
            ChatCompletionRequest request = new ChatCompletionRequest(stageProperties.model(), stageProperties.temperature(), agentSystemPrompt, prompt, previousMemory);
            String compacted = chatProviderRegistry.get(stageProperties.chatProviderId()).call(request);
            if (compacted != null && !compacted.isBlank()) {
                agentMemoryStorage.replace(conversationId, agentId, compacted);
                log.debug("[{}] Memory compacted for conversation [{}]", agentId, conversationId);
            }
        } catch (Exception ex) {
            log.warn("[{}] Memory compaction failed for conversation [{}]: {}", agentId, conversationId, ex.getMessage());
        }
    }
}