package io.workflowai.adapter.out.persistence;

import io.workflowai.adapter.out.persistence.agent.run.AgentRunEntity;
import io.workflowai.adapter.out.persistence.agent.run.AgentRunRepository;
import io.workflowai.domain.run.TriggerSource;
import io.workflowai.application.port.out.AgentRunTracker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DatabaseAgentRunTrackerAdapter implements AgentRunTracker {

    private final AgentRunRepository repository;

    public DatabaseAgentRunTrackerAdapter(AgentRunRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public UUID start(TriggerSource triggerSource, UUID agentId, UUID conversationId) {
        return repository.save(new AgentRunEntity(triggerSource, agentId, conversationId)).id();
    }

    @Override
    @Transactional
    public void complete(UUID runId) {
        repository.findById(runId).ifPresent(run -> {
            run.complete();
            repository.save(run);
        });
    }

    @Override
    @Transactional
    public void fail(UUID runId, String errorMessage) {
        repository.findById(runId).ifPresent(run -> {
            run.fail(errorMessage);
            repository.save(run);
        });
    }
}
