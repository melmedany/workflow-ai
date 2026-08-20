package io.workflowai.bootstrap.config;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.execution.ResponseValidator;
import io.workflowai.application.execution.stage.ClassificationStage;
import io.workflowai.application.execution.stage.CompactMemoryStage;
import io.workflowai.application.execution.stage.CompleteStage;
import io.workflowai.application.execution.stage.CreateTaskStage;
import io.workflowai.application.execution.stage.DecisionResponseGenerator;
import io.workflowai.application.execution.stage.ExecuteWorkflowStage;
import io.workflowai.application.execution.stage.GenerateClarificationStage;
import io.workflowai.application.execution.stage.GenerateGreetingStage;
import io.workflowai.application.execution.stage.GenerateRedirectStage;
import io.workflowai.application.execution.stage.GenerateRefusalStage;
import io.workflowai.application.execution.stage.LoadMemoryStage;
import io.workflowai.application.execution.stage.PersistResponseStage;
import io.workflowai.application.execution.stage.PersistUserMessageStage;
import io.workflowai.application.execution.stage.SelfVerificationStage;
import io.workflowai.application.execution.stage.StageSettings;
import io.workflowai.application.port.in.TaskUseCase;
import io.workflowai.application.port.out.AgentMemoryStorage;
import io.workflowai.application.port.out.ConversationMessageStorage;
import io.workflowai.application.port.out.NotificationChannel;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import io.workflowai.bootstrap.StagesProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static io.workflowai.application.execution.stage.StageSettings.StageSetting;

@Configuration
class StagesBeansConfig {

    @Bean
    StageSettings stageSettings(StagesProperties properties, ChatProviderRegistry registry) {
        List<StageSetting> settings = properties.stages().stream()
                .map(stage -> new StageSetting(
                        stage.stageId(), stage.chatProviderId(), stage.model(), stage.temperature()))
                .toList();
        settings.forEach(setting -> registry.validate(setting.chatProviderId(), setting.model()));
        return new StageSettings(settings);
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
    ClassificationStage classificationStage(ChatProviderRegistry registry, StageSettings settings,
                                            JsonMapper jsonMapper, List<WorkflowEventStreamer> streamers) {
        return new ClassificationStage(registry, settings, jsonMapper, streamers);
    }

    @Bean
    ExecuteWorkflowStage executeWorkflowStage(ChatProviderRegistry registry, ResponseValidator responseValidator,
                                              List<WorkflowEventStreamer> streamers) {
        return new ExecuteWorkflowStage(registry, responseValidator, streamers);
    }

    @Bean
    PersistResponseStage persistResponseStage(ConversationMessageStorage conversationMessageStorage, List<WorkflowEventStreamer> streamers) {
        return new PersistResponseStage(conversationMessageStorage, streamers);
    }

    @Bean
    CreateTaskStage createTaskStage(TaskUseCase taskUseCase, PersistResponseStage persistResponseStage,
                                    List<WorkflowEventStreamer> streamers) {
        return new CreateTaskStage(taskUseCase, persistResponseStage, streamers);
    }

    @Bean
    DecisionResponseGenerator decisionResponseGenerator(ChatProviderRegistry registry, StageSettings settings) {
        return new DecisionResponseGenerator(registry, settings);
    }

    @Bean
    GenerateClarificationStage generateClarificationStage(ChatProviderRegistry registry, StageSettings settings,
                                                          PersistResponseStage persistResponseStage,
                                                          List<WorkflowEventStreamer> streamers) {
        return new GenerateClarificationStage(registry, settings, persistResponseStage, streamers);
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
    SelfVerificationStage selfVerificationStage(ChatProviderRegistry registry,
                                                ResponseValidator responseValidator,
                                                PersistResponseStage persistResponseStage,
                                                List<WorkflowEventStreamer> streamers) {
        return new SelfVerificationStage(registry, responseValidator, persistResponseStage, streamers);
    }

    @Bean
    CompactMemoryStage compactMemoryStage(ChatProviderRegistry registry, StageSettings settings,
                                          AgentMemoryStorage memoryStorage, List<WorkflowEventStreamer> streamers) {
        return new CompactMemoryStage(registry, settings, memoryStorage, streamers);
    }

    @Bean
    CompleteStage completeStage(List<WorkflowEventStreamer> streamers,
                                List<NotificationChannel> notificationChannels) {
        return new CompleteStage(streamers, notificationChannels);
    }
}
