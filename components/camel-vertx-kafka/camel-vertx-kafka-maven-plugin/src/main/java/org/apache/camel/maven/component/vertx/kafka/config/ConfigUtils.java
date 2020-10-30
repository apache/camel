package org.apache.camel.maven.component.vertx.kafka.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.common.config.ConfigDef;

public final class ConfigUtils {

    private static final int ONE_UNIT = 1;

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

    /**
     * This will print time in human readable format from milliseconds. Examples: 500 -> will produce 500ms 1300 -> will
     * produce 1s300ms 310300 -> will produce 5m10s300ms 6600000 -> will produce 1h50m
     *
     * @param  timeMilli time in milliseconds
     * @return           time in string
     */
    public static String toTimeAsString(final long timeMilli) {
        if (timeMilli < 1) {
            return "0ms";
        }
        final StringBuilder stringBuilder = new StringBuilder();
        scanTimeUnits(timeMilli, stringBuilder);

        return stringBuilder.toString();
    }

    private static long scanTimeUnits(final long timeInMillis, final StringBuilder timeBuilder) {
        if (timeInMillis >= Duration.ofDays(ONE_UNIT).toMillis()) {
            final long proceededTime
                    = processSingleTimeUnit(timeInMillis, Duration.ofDays(ONE_UNIT).toMillis(), timeBuilder, "d");
            return scanTimeUnits(proceededTime, timeBuilder);
        } else if (timeInMillis >= Duration.ofHours(ONE_UNIT).toMillis()) {
            final long proceededTime
                    = processSingleTimeUnit(timeInMillis, Duration.ofHours(ONE_UNIT).toMillis(), timeBuilder, "h");
            return scanTimeUnits(proceededTime, timeBuilder);
        } else if (timeInMillis >= Duration.ofMinutes(ONE_UNIT).toMillis()) {
            final long proceededTime
                    = processSingleTimeUnit(timeInMillis, Duration.ofMinutes(ONE_UNIT).toMillis(), timeBuilder, "m");
            return scanTimeUnits(proceededTime, timeBuilder);
        } else if (timeInMillis >= Duration.ofSeconds(ONE_UNIT).toMillis()) {
            final long proceededTime
                    = processSingleTimeUnit(timeInMillis, Duration.ofSeconds(ONE_UNIT).toMillis(), timeBuilder, "s");
            return scanTimeUnits(proceededTime, timeBuilder);
        } else if (timeInMillis >= ONE_UNIT) {
            final long proceededTime = processSingleTimeUnit(timeInMillis, ONE_UNIT, timeBuilder, "ms");
            return scanTimeUnits(proceededTime, timeBuilder);
        }
        return 0;
    }

    private static long processSingleTimeUnit(
            final long timeInMillis, final long timeUnitMillis, final StringBuilder timeBuilder, final String timeSymbol) {
        timeBuilder.append(calculateTimeUnit(timeInMillis, timeUnitMillis)).append(timeSymbol);
        return timeInMillis % timeUnitMillis;
    }

    private static long calculateTimeUnit(final long timeInMillis, final long baseTimeUnit) {
        return timeInMillis / baseTimeUnit;
    }
}
