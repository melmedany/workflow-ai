package io.workflowai.application.execution;

import io.workflowai.domain.exceptions.ChatProviderCallException;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.application.port.out.ChatProvider;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ChatProviderRegistry {

    private final Map<ChatProviderId, ChatProvider> providers;

    public ChatProviderRegistry(List<ChatProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toConcurrentMap(ChatProvider::getId, Function.identity()));
    }

    public ChatProvider get(ChatProviderId providerId) {
        ChatProvider provider = providers.get(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown provider: %s. Available: %s".formatted(providerId, providers.keySet()));
        }
        return provider;
    }

    public Map<ChatProviderId, Set<String>> supportedChatProviders() {
        return providers.values().stream()
                .collect(Collectors.toMap(ChatProvider::getId, ChatProvider::supportedModels));
    }

    public void validate(ChatProviderId providerId, String model) {
        ChatProvider provider = get(providerId);
        if (!provider.supportsModel(model)) {
            throw new ChatProviderCallException(providerId, "Unsupported model [%s]. Supported models: %s".formatted(
                    model, provider.supportedModels()));
        }
    }
}