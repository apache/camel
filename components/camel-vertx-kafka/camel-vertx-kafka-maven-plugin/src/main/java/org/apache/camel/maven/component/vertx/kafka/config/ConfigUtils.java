package org.apache.camel.maven.component.vertx.kafka.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.common.config.ConfigDef;

public final class ConfigUtils {

    private ConfigUtils() {
    }

    public static Map<String, ConfigDef.ConfigKey> extractConsumerOnlyFields(
            final Map<String, ConfigDef.ConfigKey> consumerConfigs,
            final Map<String, ConfigDef.ConfigKey> producerConfigs) {
        final Map<String, ConfigDef.ConfigKey> results = new HashMap<>(consumerConfigs);
        final Set<String> wantedFields = extractConsumerOnlyFields(consumerConfigs.keySet(), producerConfigs.keySet());

        results.keySet().retainAll(wantedFields);

        return results;
    }

    public static Map<String, ConfigDef.ConfigKey> extractProducerOnlyFields(
            final Map<String, ConfigDef.ConfigKey> consumerConfigs,
            final Map<String, ConfigDef.ConfigKey> producerConfigs) {
        final Map<String, ConfigDef.ConfigKey> results = new HashMap<>(producerConfigs);
        final Set<String> wantedFields = extractProducerOnlyFields(consumerConfigs.keySet(), producerConfigs.keySet());

        results.keySet().retainAll(wantedFields);

        return results;
    }

    public static Map<String, ConfigDef.ConfigKey> extractCommonFields(
            final Map<String, ConfigDef.ConfigKey> consumerConfigs,
            final Map<String, ConfigDef.ConfigKey> producerConfigs) {
        final Map<String, ConfigDef.ConfigKey> results = new HashMap<>(consumerConfigs);
        results.putAll(producerConfigs);
        final Set<String> wantedFields = extractCommonFields(consumerConfigs.keySet(), producerConfigs.keySet());

        results.keySet().retainAll(wantedFields);

        return results;
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
