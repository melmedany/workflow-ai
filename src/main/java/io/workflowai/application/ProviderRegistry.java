package io.workflowai.application;

import io.workflowai.domain.model.ProviderOption;
import io.workflowai.ports.outbound.LlmProviderPort;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProviderRegistry {

    private final Map<String, LlmProviderPort> providers;

    public ProviderRegistry(List<LlmProviderPort> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(LlmProviderPort::getProviderName, Function.identity()));
    }

    public LlmProviderPort get(String providerName) {
        LlmProviderPort provider = providers.get(providerName);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown provider: " + providerName + ". Available: " + providers.keySet());
        }
        return provider;
    }

    public List<ProviderOption> supportedOptions() {
        return providers.values().stream()
                .sorted(Comparator.comparing(LlmProviderPort::getProviderName))
                .map(provider -> new ProviderOption(provider.getProviderName(), provider.supportedModels()))
                .toList();
    }
}
