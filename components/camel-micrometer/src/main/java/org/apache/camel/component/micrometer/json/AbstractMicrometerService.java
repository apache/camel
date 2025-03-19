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
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.service.ServiceSupport;

import static org.apache.camel.component.micrometer.MicrometerConstants.APP_INFO_METER_NAME;

public class AbstractMicrometerService extends ServiceSupport {

    private CamelContext camelContext;
    private MeterRegistry meterRegistry;
    private boolean prettyPrint = true;
    private boolean skipCamelInfo = false;
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
        } catch (JsonProcessingException e) {
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
        } catch (JsonProcessingException e) {
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

        if (!this.skipCamelInfo) {
            registerAppInfo(meterRegistry);
        }

        // json mapper
        this.mapper = new ObjectMapper()
                .registerModule(new MicrometerModule(getDurationUnit(), getMatchingNames(), getMatchingTags()));
        this.secondsMapper = getDurationUnit() == TimeUnit.SECONDS
                ? this.mapper
                : new ObjectMapper()
                        .registerModule(new MicrometerModule(TimeUnit.SECONDS, getMatchingNames(), getMatchingTags()));
    }

    @Override
    protected void doStop() {
        // noop
    }

    // This method does a best effort attempt to recover information about versioning of the runtime.
    // It is also in charge to include the information in the meterRegistry passed in.
    private void registerAppInfo(MeterRegistry meterRegistry) {
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
