package io.workflowai.adapter.in.rest;

import io.workflowai.adapter.in.rest.dto.TaskResponse;
import io.workflowai.application.port.in.TaskUseCase;
import io.workflowai.domain.task.ConversationTask;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agents")
public class TaskController {

    private final TaskUseCase taskUseCase;

    public TaskController(TaskUseCase taskUseCase) {
        this.taskUseCase = taskUseCase;
    }

    @GetMapping("/{agentId}/conversations/{conversationId}/tasks")
    public ResponseEntity<List<TaskResponse>> getTasks(@PathVariable UUID agentId, @PathVariable UUID conversationId) {
        List<TaskResponse> tasks = taskUseCase.listByConversation(agentId, conversationId).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(tasks);
    }

    @PostMapping("/{agentId}/conversations/{conversationId}/tasks/{taskId}/pause")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pause(@PathVariable UUID agentId, @PathVariable UUID conversationId, @PathVariable UUID taskId) {
        taskUseCase.pause(agentId, conversationId, taskId);
    }

    @PostMapping("/{agentId}/conversations/{conversationId}/tasks/{taskId}/resume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resume(@PathVariable UUID agentId, @PathVariable UUID conversationId, @PathVariable UUID taskId) {
        taskUseCase.resume(agentId, conversationId, taskId);
    }

    @PostMapping("/{agentId}/conversations/{conversationId}/tasks/{taskId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID agentId, @PathVariable UUID conversationId, @PathVariable UUID taskId) {
        taskUseCase.cancel(agentId, conversationId, taskId);
    }

    private TaskResponse toResponse(ConversationTask task) {
        return new TaskResponse(
                task.id(),
                task.agentId(),
                task.conversationId(),
                task.definition().name(),
                task.schedule().type().name(),
                task.schedule().duration(),
                task.schedule().status().name(),
                task.runInfo().lastRunAt(),
                task.runInfo().lastRunStatus(),
                task.nextRunAt()
        );
    }
}