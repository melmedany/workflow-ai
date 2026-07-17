package io.workflowai.application;

import io.workflowai.application.agents.BaseAgent;
import io.workflowai.domain.agents.Agent;
import io.workflowai.domain.exceptions.AgentNotFoundException;
import io.workflowai.domain.model.AgentConfig;
import io.workflowai.domain.model.AgentDefinition;
import io.workflowai.domain.model.PolicyConfig;
import io.workflowai.domain.workflow.WorkflowPolicy;
import io.workflowai.ports.inbound.AgentPort;
import io.workflowai.ports.outbound.AgentDefinitionStoragePort;
import io.workflowai.ports.outbound.AgentMemoryStoragePort;
import io.workflowai.ports.outbound.LlmProviderPort;
import io.workflowai.ports.outbound.MessageStoragePort;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AgentService implements AgentPort {

    // TODO: cache can have bad performance as agents grows
    private final Map<UUID, Agent> agentsCache;
    private final AgentDefinitionStoragePort definitionStoragePort;
    private final ProviderRegistry providerRegistry;
    private final MessageStoragePort messageStoragePort;
    private final AgentMemoryStoragePort agentMemoryStoragePort;
    private final JsonMapper jsonMapper;

    public AgentService(
            AgentDefinitionStoragePort definitionStoragePort,
            ProviderRegistry providerRegistry,
            MessageStoragePort messageStoragePort,
            AgentMemoryStoragePort agentMemoryStoragePort,
            JsonMapper jsonMapper) {
        this.definitionStoragePort = definitionStoragePort;
        this.providerRegistry = providerRegistry;
        this.messageStoragePort = messageStoragePort;
        this.agentMemoryStoragePort = agentMemoryStoragePort;
        this.jsonMapper = jsonMapper;
        this.agentsCache = buildAgentsCache();
    }

    @Override
    public Agent get(UUID id) {
        Agent agent = agentsCache.get(id);
        if (agent == null) {
            agent = definitionStoragePort.findById(id)
                    .map(this::createAgent)
                    .orElseThrow(() -> new AgentNotFoundException(id));
            agentsCache.put(id, agent);
        }
        return agent;
    }

    @Override
    public void reload(UUID id) {
        agentsCache.remove(id);
        Agent agent = definitionStoragePort.findById(id)
                .map(this::createAgent)
                .orElseThrow(() -> new AgentNotFoundException(id));
        agentsCache.put(id, agent);
    }

    @Override
    public List<Agent> getAll() {
        return agentsCache.values().stream().toList();
    }

    private Map<UUID, Agent> buildAgentsCache() {
        List<Agent> agents = definitionStoragePort.findAll().stream()
                .map(this::createAgent)
                .toList();
        return agents.stream().collect(Collectors.toMap(a -> a.getConfig().id(), Function.identity()));
    }

    private Agent createAgent(AgentDefinition definition) {
        AgentConfig config = toAgentConfig(definition);
        PolicyConfig policyConfig = config.policyConfig();
        LlmProviderPort llmProvider = providerRegistry.get(config.provider());
        return new BaseAgent(config, llmProvider, messageStoragePort, agentMemoryStoragePort, new WorkflowPolicy(
                policyConfig.capabilities(),
                policyConfig.greetings(),
                policyConfig.refuseMessages(),
                policyConfig.redirectMessages(),
                policyConfig.maxRetries(),
                config.validationEnabled()),
                jsonMapper) {
            @Override
            public List<String> tags() {
                return policyConfig.capabilities();
            }
        };
    }

    private AgentConfig toAgentConfig(AgentDefinition definition) {
        return new AgentConfig() {
            @Override
            public UUID id() {
                return definition.agentId();
            }

            @Override
            public String displayName() {
                return definition.details().displayName();
            }

            @Override
            public String description() {
                return definition.details().description();
            }

            @Override
            public boolean enabled() {
                return definition.details().enabled();
            }

            @Override
            public String provider() {
                return definition.llmConfig().provider();
            }

            @Override
            public String model() {
                return definition.llmConfig().model();
            }

            @Override
            public double temperature() {
                return definition.llmConfig().temperature();
            }

            @Override
            public String systemPrompt() {
                return definition.llmConfig().agentPrompt();
            }

            @Override
            public boolean memoryEnabled() {
                return definition.llmConfig().memoryEnabled();
            }

            @Override
            public boolean validationEnabled() {
                return definition.llmConfig().validationEnabled();
            }

            @Override
            public int memoryLimit() {
                return definition.llmConfig().memoryLimit();
            }

            @Override
            public PolicyConfig policyConfig() {
                return definition.policyConfig();
            }
        };
    }
}
