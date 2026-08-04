package io.workflowai.bootstrap.config;

import io.workflowai.application.agent.AgentExecutionService;
import io.workflowai.application.conversation.ConversationService;
import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.execution.AgentService;
import io.workflowai.application.execution.stage.ClassificationStage;
import io.workflowai.application.execution.stage.CompactMemoryStage;
import io.workflowai.application.execution.stage.CompleteStage;
import io.workflowai.application.execution.stage.DecisionResponseGenerator;
import io.workflowai.application.execution.stage.ExecuteWorkflowStage;
import io.workflowai.application.execution.stage.GenerateClarificationStage;
import io.workflowai.application.execution.stage.GenerateGreetingStage;
import io.workflowai.application.execution.stage.GenerateRedirectStage;
import io.workflowai.application.execution.stage.GenerateRefusalStage;
import io.workflowai.application.execution.stage.LoadMemoryStage;
import io.workflowai.application.execution.stage.PersistResponseStage;
import io.workflowai.application.execution.stage.PersistUserMessageStage;
import io.workflowai.application.execution.ResponseValidator;
import io.workflowai.application.execution.stage.SelfVerificationStage;
import io.workflowai.application.execution.stage.StageSettings;
import io.workflowai.application.execution.workflow.WorkflowFactory;
import io.workflowai.application.port.out.AgentDefinitionStorage;
import io.workflowai.application.port.out.AgentMemoryStorage;
import io.workflowai.application.port.out.AgentRunTracker;
import io.workflowai.application.port.out.ChatProvider;
import io.workflowai.application.port.out.ConversationMessageStorage;
import io.workflowai.application.port.out.ConversationStorage;
import io.workflowai.application.port.out.NotificationChannel;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import io.workflowai.bootstrap.StagesProperties;
import io.workflowai.domain.workflow.WorkflowExecutorFactory;
import io.workflowai.domain.workflow.WorkflowStage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Configuration
class ApplicationBeansConfig {

    @Bean
    StageSettings stageSettings(StagesProperties properties) {
        return new StageSettings(properties.stages().stream()
                .map(stage -> new StageSettings.StageSetting(
                        stage.stageId(), stage.chatProviderId(), stage.model(), stage.temperature()))
                .toList());
    }

    @Bean
    ChatProviderRegistry chatProviderRegistry(List<ChatProvider> providers) {
        return new ChatProviderRegistry(providers);
    }

    @Bean
    PersistUserMessageStage persistUserMessageStage(ConversationMessageStorage conversationMessageStorage, List<WorkflowEventStreamer> streamers) {
        return new PersistUserMessageStage(conversationMessageStorage, streamers);
    }

    @Bean
    ResponseValidator responseValidator(JsonMapper jsonMapper) {
        return new ResponseValidator(jsonMapper);
    }

    @Bean
    LoadMemoryStage loadMemoryStage(AgentMemoryStorage memoryStorage, List<WorkflowEventStreamer> streamers) {
        return new LoadMemoryStage(memoryStorage, streamers);
    }

    @Bean
    ClassificationStage classificationStage(ChatProviderRegistry providers, StageSettings settings,
                                            JsonMapper jsonMapper, List<WorkflowEventStreamer> streamers) {
        return new ClassificationStage(providers, settings, jsonMapper, streamers);
    }

    @Bean
    ExecuteWorkflowStage executeWorkflowStage(ChatProviderRegistry providers, ResponseValidator responseValidator,
                                              List<WorkflowEventStreamer> streamers) {
        return new ExecuteWorkflowStage(providers, responseValidator, streamers);
    }

    @Bean
    PersistResponseStage persistResponseStage(ConversationMessageStorage conversationMessageStorage, List<WorkflowEventStreamer> streamers) {
        return new PersistResponseStage(conversationMessageStorage, streamers);
    }

    @Bean
    DecisionResponseGenerator decisionResponseGenerator(ChatProviderRegistry providers, StageSettings settings) {
        return new DecisionResponseGenerator(providers, settings);
    }

    @Bean
    GenerateClarificationStage generateClarificationStage(ChatProviderRegistry providers, StageSettings settings,
                                                          PersistResponseStage persistResponseStage,
                                                          List<WorkflowEventStreamer> streamers) {
        return new GenerateClarificationStage(providers, settings, persistResponseStage, streamers);
    }

    @Bean
    GenerateRedirectStage generateRedirectStage(DecisionResponseGenerator generator,
                                                PersistResponseStage persistResponseStage,
                                                List<WorkflowEventStreamer> streamers) {
        return new GenerateRedirectStage(generator, persistResponseStage, streamers);
    }

    @Bean
    GenerateGreetingStage generateGreetingStage(DecisionResponseGenerator generator,
                                                PersistResponseStage persistResponseStage,
                                                List<WorkflowEventStreamer> streamers) {
        return new GenerateGreetingStage(generator, persistResponseStage, streamers);
    }

    @Bean
    GenerateRefusalStage generateRefusalStage(DecisionResponseGenerator generator,
                                              PersistResponseStage persistResponseStage,
                                              List<WorkflowEventStreamer> streamers) {
        return new GenerateRefusalStage(generator, persistResponseStage, streamers);
    }

    @Bean
    SelfVerificationStage selfVerificationStage(ChatProviderRegistry providers,
                                                ResponseValidator responseValidator,
                                                PersistResponseStage persistResponseStage,
                                                List<WorkflowEventStreamer> streamers) {
        return new SelfVerificationStage(providers, responseValidator, persistResponseStage, streamers);
    }

    @Bean
    CompactMemoryStage compactMemoryStage(ChatProviderRegistry providers, StageSettings settings,
                                          AgentMemoryStorage memoryStorage) {
        return new CompactMemoryStage(providers, settings, memoryStorage);
    }

    @Bean
    CompleteStage completeStage(List<WorkflowEventStreamer> streamers,
                                List<NotificationChannel> notificationChannels) {
        return new CompleteStage(streamers, notificationChannels);
    }

    @Bean
    WorkflowExecutorFactory workflowExecutorFactory(List<WorkflowStage> stages) {
        return new WorkflowExecutorFactory(stages);
    }

    @Bean
    WorkflowFactory workflowFactory(StageSettings settings, ChatProviderRegistry providers,
                                    WorkflowExecutorFactory executorFactory) {
        return new WorkflowFactory(settings, providers, executorFactory);
    }

    @Bean
    AgentService agentService(AgentDefinitionStorage definitions, WorkflowFactory workflowFactory,
                              AgentRunTracker runs, WorkflowEventStreamer events) {
        return new AgentService(definitions, workflowFactory, runs, events);
    }

    @Bean
    AgentExecutionService agentAdminService(AgentDefinitionStorage definitions, ChatProviderRegistry providers,
                                            WorkflowExecutorFactory workflowExecutorFactory) {
        return new AgentExecutionService(definitions, providers, workflowExecutorFactory);
    }

    @Bean
    ConversationService conversationService(ConversationStorage conversations, ConversationMessageStorage messages) {
        return new ConversationService(conversations, messages);
    }
}
