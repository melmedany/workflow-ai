package io.workflowai.domain.model;

import java.util.Set;

public record ProviderOption(String provider, Set<String> models) {
}