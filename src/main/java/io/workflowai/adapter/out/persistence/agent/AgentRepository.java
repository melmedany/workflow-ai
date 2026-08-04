package io.workflowai.adapter.out.persistence.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<AgentEntity, UUID> {

    @Query(value = "SELECT * FROM agents WHERE details ->> 'enabled' = 'true'", nativeQuery = true)
    List<AgentEntity> findEnabledAgents();
}