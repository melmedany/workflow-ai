package io.workflowai.application;

import io.workflowai.domain.exceptions.LlmCallException;
import io.workflowai.ports.outbound.LlmProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LlmProviderRegistry {

    private final Map<LlmProviderId, LlmProvider> providers;

    public LlmProviderRegistry(List<LlmProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toConcurrentMap(LlmProvider::getId, Function.identity()));
    }

    public LlmProvider get(LlmProviderId providerId) {
        LlmProvider provider = providers.get(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown provider: %s. Available: %s".formatted(providerId, providers.keySet()));
        }
        return provider;
    }

    public Map<LlmProviderId, Set<String>> supportedLlmProvider() {
        return providers.values().stream()
                .collect(Collectors.toMap(LlmProvider::getId, LlmProvider::supportedModels));
    }

    // TODO add more validations
    public void validate(LlmProviderId providerId, String model) {
        LlmProvider provider = get(providerId);
        if (!provider.supportsModel(model)) {
            throw new LlmCallException(providerId, "Unsupported model [%s]. Supported models: %s".formatted(
                    model, provider.supportedModels()));
        }
    }
}
