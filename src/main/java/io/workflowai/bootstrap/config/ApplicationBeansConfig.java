package io.workflowai.bootstrap.config;

import io.workflowai.application.agent.AgentDefinitionService;
import io.workflowai.application.conversation.ConversationService;
import io.workflowai.application.execution.AgentService;
import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.execution.stage.StageSettings;
import io.workflowai.application.execution.workflow.WorkflowFactory;
import io.workflowai.application.port.in.AgentUseCase;
import io.workflowai.application.port.in.ConversationUseCase;
import io.workflowai.application.port.in.TaskUseCase;
import io.workflowai.application.port.out.AgentDefinitionStorage;
import io.workflowai.application.port.out.AgentRunTracker;
import io.workflowai.application.port.out.ChatProvider;
import io.workflowai.application.port.out.ConversationMessageStorage;
import io.workflowai.application.port.out.ConversationStorage;
import io.workflowai.application.port.out.ConversationTaskStorage;
import io.workflowai.application.port.out.TaskScheduler;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import io.workflowai.application.task.TaskService;
import io.workflowai.domain.workflow.WorkflowExecutorFactory;
import io.workflowai.domain.workflow.WorkflowStage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
class ApplicationBeansConfig {

    @Bean
    ChatProviderRegistry chatProviderRegistry(List<ChatProvider> chatProviders) {
        return new ChatProviderRegistry(chatProviders);
    }


    @Bean
    TaskService taskService(ConversationTaskStorage storage, TaskScheduler scheduler) {
        return new TaskService(storage, scheduler);
    }

    @Bean
    WorkflowExecutorFactory workflowExecutorFactory(List<WorkflowStage> stages) {
        return new WorkflowExecutorFactory(stages);
    }

    @Bean
    WorkflowFactory workflowFactory(StageSettings settings, ChatProviderRegistry registry,
                                    WorkflowExecutorFactory executorFactory) {
        return new WorkflowFactory(settings, registry, executorFactory);
    }

    @Bean
    AgentService agentService(AgentDefinitionStorage definitions, WorkflowFactory workflowFactory,
                              AgentRunTracker runs, WorkflowEventStreamer events, ConversationUseCase conversationService) {
        return new AgentService(definitions, workflowFactory, runs, events, conversationService);
    }

    @Bean
    AgentDefinitionService agentAdminService(AgentDefinitionStorage definitions, ChatProviderRegistry registry,
                                             WorkflowExecutorFactory workflowExecutorFactory, AgentUseCase agentService) {
        return new AgentDefinitionService(definitions, registry, workflowExecutorFactory, agentService);
    }

    @Bean
    ConversationService conversationService(ConversationStorage conversations, ConversationMessageStorage messages,
                                            TaskUseCase taskUseCase) {
        return new ConversationService(conversations, messages, taskUseCase);
    }
}
