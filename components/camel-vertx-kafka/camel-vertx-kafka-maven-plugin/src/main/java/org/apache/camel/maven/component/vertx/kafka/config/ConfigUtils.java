package org.apache.camel.maven.component.vertx.kafka.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ConfigUtils {

    private ConfigUtils() {
    }

    public static Set<String> extractConsumerOnlyFields(final Set<String> consumerConfigs, final Set<String> producerConfigs) {
        final Set<String> results = new HashSet<>(consumerConfigs);
        results.removeAll(producerConfigs);

        return results;
    }

    public static Set<String> extractProducerOnlyFields(final Set<String> consumerConfigs, final Set<String> producerConfigs) {
        final Set<String> results = new HashSet<>(producerConfigs);
        results.removeAll(consumerConfigs);

        return results;
    }

    public static Set<String> extractCommonFields(final Set<String> consumerConfigs, final Set<String> producerConfigs) {
        final Set<String> results = new HashSet<>(consumerConfigs);
        results.retainAll(producerConfigs);

        return results;
    }
}
