/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.micrometer.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

import static org.apache.camel.component.micrometer.MicrometerConstants.APP_INFO_METER_NAME;

public class AbstractMicrometerService extends ServiceSupport {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractMicrometerService.class);

    private CamelContext camelContext;
    private MeterRegistry meterRegistry;
    private boolean prettyPrint = true;
    private boolean skipCamelInfo = false;
    private boolean logMetricsOnShutdown = false;
    private String logMetricsOnShutdownFilters[];
    private TimeUnit durationUnit = TimeUnit.MILLISECONDS;
    private Iterable<Tag> matchingTags = Tags.empty();
    private Predicate<String> matchingNames;
    private transient ObjectMapper mapper;
    private transient ObjectMapper secondsMapper;

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    public void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public boolean isSkipCamelInfo() {
        return skipCamelInfo;
    }

    public void setSkipCamelInfo(boolean skipCamelInfo) {
        this.skipCamelInfo = skipCamelInfo;
    }

    public boolean isLogMetricsOnShutdown() {
        return logMetricsOnShutdown;
    }

    public void setLogMetricsOnShutdown(boolean logMetricsOnShutdown) {
        this.logMetricsOnShutdown = logMetricsOnShutdown;
    }

    public String[] getLogMetricsOnShutdownFilters() {
        return logMetricsOnShutdownFilters;
    }

    public void setLogMetricsOnShutdownFilters(String... logMetricsOnShutdownFilters) {
        this.logMetricsOnShutdownFilters = logMetricsOnShutdownFilters;
    }

    public TimeUnit getDurationUnit() {
        return durationUnit;
    }

    public void setDurationUnit(TimeUnit durationUnit) {
        this.durationUnit = durationUnit;
    }

    public Iterable<Tag> getMatchingTags() {
        return matchingTags;
    }

    public void setMatchingTags(Iterable<Tag> matchingTags) {
        this.matchingTags = matchingTags;
    }

    public Predicate<String> getMatchingNames() {
        return matchingNames;
    }

    public void setMatchingNames(Predicate<String> matchingNames) {
        this.matchingNames = matchingNames;
    }

    public String dumpStatisticsAsJson() {
        ObjectWriter writer = mapper.writer();
        if (isPrettyPrint()) {
            writer = writer.withDefaultPrettyPrinter();
        }
        try {
            return writer.writeValueAsString(getMeterRegistry());
        } catch (JacksonException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    public String dumpStatisticsAsJsonTimeUnitSeconds() {
        ObjectWriter writer = secondsMapper.writer();
        if (isPrettyPrint()) {
            writer = writer.withDefaultPrettyPrinter();
        }
        try {
            return writer.writeValueAsString(getMeterRegistry());
        } catch (JacksonException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    @Override
    protected void doStart() {
        if (meterRegistry == null) {
            Registry camelRegistry = getCamelContext().getRegistry();
            meterRegistry = camelRegistry.lookupByNameAndType(MicrometerConstants.METRICS_REGISTRY_NAME, MeterRegistry.class);
            // create a new metricsRegistry by default
            if (meterRegistry == null) {
                meterRegistry = new SimpleMeterRegistry();
            }
        }

        if (!isSkipCamelInfo()) {
            registerAppInfo(meterRegistry);
        }

        // json mapper
        this.mapper = JsonMapper.builder()
                .addModule(new MicrometerModule(getDurationUnit(), getMatchingNames(), getMatchingTags())).build();
        this.secondsMapper = getDurationUnit() == TimeUnit.SECONDS
                ? this.mapper
                : JsonMapper.builder()
                        .addModule(new MicrometerModule(TimeUnit.SECONDS, getMatchingNames(), getMatchingTags())).build();
    }

    @Override
    protected void doStop() {
        if (logMetricsOnShutdown) {
            LOG.warn("Micrometer service is stopping, here a list of metrics collected so far.");
            // Default: all metrics
            logMetricsOnShutdown(logMetricsOnShutdownFilters == null ? new String[] { "*" } : logMetricsOnShutdownFilters);
        }
    }

    static boolean matchesFilter(String metricName, String... filters) {
        for (String filter : filters) {
            if (filter.contains("*")) {
                if (metricName.contains(filter.replace("*", ""))) {
                    return true;
                }
            } else if (filter.equals(metricName)) {
                return true;
            }
        }
        return false;
    }

    static Map<String, Object> convertMeterToMap(Meter meter) {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("name", meter.getId().getName());
        logEntry.put("tags", meter.getId().getTags().stream()
                .collect(Collectors.toMap(Tag::getKey, Tag::getValue)));

        if (meter instanceof Gauge g) {
            logEntry.put("type", "gauge");
            logEntry.put("value", g.value());
        } else if (meter instanceof Counter c) {
            logEntry.put("type", "counter");
            logEntry.put("value", c.count());
        } else if (meter instanceof Timer t) {
            logEntry.put("type", "timer");
            logEntry.put("totalTimeMs", t.totalTime(TimeUnit.MILLISECONDS));
            logEntry.put("count", t.count());
            logEntry.put("maxTimeMs", t.max(TimeUnit.MILLISECONDS));
        } else if (meter instanceof DistributionSummary ds) {
            logEntry.put("type", "summary");
            logEntry.put("total", ds.totalAmount());
            logEntry.put("count", ds.count());
            logEntry.put("max", ds.max());
        } else if (meter instanceof FunctionCounter fc) {
            logEntry.put("type", "functionCounter");
            logEntry.put("value", fc.count());
        } else if (meter instanceof FunctionTimer ft) {
            logEntry.put("type", "functionTimer");
            logEntry.put("count", ft.count());
            logEntry.put("totalTimeMs", ft.totalTime(TimeUnit.MILLISECONDS));
            logEntry.put("meanMs", ft.mean(TimeUnit.MILLISECONDS));
        } else {
            logEntry.put("type", meter.getId().getType().name());
        }

        return logEntry;
    }

    void logMetricsOnShutdown(String... filters) {
        meterRegistry.getMeters().stream()
                .filter(m -> AbstractMicrometerService.matchesFilter(m.getId().getName(), filters))
                .map(AbstractMicrometerService::convertMeterToMap)
                .forEach(logEntry -> {
                    try {
                        // We put on warn level to make sure it is printed even if the log is
                        // at higher levels. Important: we also include a start and end tag to make sure the
                        // scraper can more easily identify the metric content.
                        String metric = "#METRIC-START#" + mapper.writeValueAsString(logEntry) + "#METRIC-END#";
                        LOG.warn(metric);
                    } catch (Exception e) {
                        LOG.error("Error logging metric " + logEntry.get("name"), e);
                    }
                });
    }

    // This method does a best effort attempt to recover information about versioning of the runtime.
    // It is also in charge to include the information in the meterRegistry passed in.
    private void registerAppInfo(MeterRegistry meterRegistry) {
        if (meterRegistry.find(APP_INFO_METER_NAME).gauge() == null) {
            Optional<RuntimeInfo> rt = RuntimeInfo.quarkus();
            if (!rt.isPresent()) {
                rt = RuntimeInfo.springboot();
            }
            if (!rt.isPresent()) {
                // If not other runtime is available, we assume we're on Camel main
                rt = Optional.of(new RuntimeInfo(RuntimeInfo.MAIN, getCamelContext().getVersion()));
            }
            meterRegistry.gaugeCollectionSize(
                    APP_INFO_METER_NAME,
                    Tags.of(
                            "camel.version", getCamelContext().getVersion(),
                            "camel.context", getCamelContext().getName(),
                            "camel.runtime.provider", rt.get().runtimeProvider,
                            "camel.runtime.version", rt.get().runtimeVersion),
                    new ArrayList<String>());
        }
    }

    private static class RuntimeInfo {
        private static String QUARKUS = "Quarkus";
        private static String SPRING_BOOT = "Spring-Boot";
        private static String MAIN = "Main";

        String runtimeProvider;
        String runtimeVersion;

        private RuntimeInfo(String runtimeProvider, String runtimeVersion) {
            this.runtimeProvider = runtimeProvider;
            this.runtimeVersion = runtimeVersion;
        }

        static Optional<RuntimeInfo> quarkus() {
            Optional<String> version = scan("io.quarkus.runtime.Application");
            if (version.isPresent()) {
                return Optional.of(new RuntimeInfo(QUARKUS, version.get()));
            }
            return Optional.empty();
        }

        static Optional<RuntimeInfo> springboot() {
            Optional<String> version = scan("org.springframework.boot.SpringApplication");
            if (version.isPresent()) {
                return Optional.of(new RuntimeInfo(SPRING_BOOT, version.get()));
            }
            return Optional.empty();
        }

        static Optional<String> scan(String fqn) {
            try {
                Class<?> clazz = Class.forName(fqn);
                Package pkg = clazz.getPackage();

                if (pkg != null) {
                    return Optional.of(pkg.getImplementationVersion());
                }
            } catch (ClassNotFoundException e) {
                // NOOP
            }

            return Optional.empty();
        }
    }

}
