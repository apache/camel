package org.apache.camel.routepolicy.quartz2;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class WatcherDefinition {

    public static final String NAME = "NAME";
    public static final String OFFSET_MILLIS = "OFFSET_MILLIS";
    public static final String MIN_COUNT = "MIN_COUNT";
    public static final String MAX_COUNT = "MAX_COUNT";

    private final Map<String, Object> configuration = new HashMap<>();
    private final String cronExpression;

    private boolean enabled = true;

    public WatcherDefinition(String cronExpression) {
        this.cronExpression = cronExpression;
        window(5, TimeUnit.MINUTES);
    }

    public WatcherDefinition name(String name) {
        configuration.put(NAME, name);
        return this;
    }

    public WatcherDefinition enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public WatcherDefinition window(long duration, TimeUnit timeUnit) {
        configuration.put(OFFSET_MILLIS, TimeUnit.MILLISECONDS.convert(duration, timeUnit));
        return this;
    }

    public WatcherDefinition min(int count) {
        configuration.put(MIN_COUNT, count);
        return this;
    }

    public WatcherDefinition max(int count) {
        configuration.put(MAX_COUNT, count);
        return this;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
