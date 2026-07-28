package io.workflowai.adapters.outbound.persistence;

import io.workflowai.adapters.outbound.persistence.agentrun.AgentRunEntity;
import io.workflowai.adapters.outbound.persistence.agentrun.AgentRunRepository;
import io.workflowai.domain.model.TriggerSource;
import io.workflowai.ports.outbound.AgentRunTracker;
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
