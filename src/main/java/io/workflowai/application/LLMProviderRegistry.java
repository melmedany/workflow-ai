package io.workflowai.application;

import io.workflowai.ports.outbound.LLLMProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LLMProviderRegistry {

    private final Map<LLMProviderId, LLLMProvider> providers;

    public LLMProviderRegistry(List<LLLMProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toConcurrentMap(LLLMProvider::getId, Function.identity()));
    }

    public LLLMProvider get(LLMProviderId providerId) {
        LLLMProvider provider = providers.get(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown provider: %s. Available: %s".formatted(providerId, providers.keySet()));
        }
        return provider;
    }

    public Map<LLMProviderId, Set<String>> supportedLLMProvider() {
        return providers.values().stream()
                .collect(Collectors.toMap(LLLMProvider::getId, LLLMProvider::supportedModels));
    }
}
