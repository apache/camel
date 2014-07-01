package org.apache.camel.metrics;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public enum MetricsType {

    GAUGE("gauge"),
    COUNTER("counter"),
    HISTOGRAM("histogram"),
    METER("meter"),
    TIMER("timer"), ;

    private static final Map<String, MetricsType> map = new HashMap<String, MetricsType>();

    private final String name;

    static {
        for (MetricsType type : EnumSet.allOf(MetricsType.class)) {
            map.put(type.name, type);
        }
    }

    private MetricsType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static MetricsType getByName(String name) {
        return map.get(name);
    }
}
