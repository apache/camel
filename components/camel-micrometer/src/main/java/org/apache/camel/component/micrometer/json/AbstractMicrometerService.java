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

public class AbstractMicrometerService extends ServiceSupport {

    private CamelContext camelContext;
    private MeterRegistry meterRegistry;
    private boolean prettyPrint = true;
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

        // json mapper
        this.mapper = new ObjectMapper().registerModule(new MicrometerModule(getDurationUnit(), getMatchingNames(), getMatchingTags()));
        this.secondsMapper = getDurationUnit() == TimeUnit.SECONDS
                ? this.mapper
                : new ObjectMapper().registerModule(new MicrometerModule(TimeUnit.SECONDS, getMatchingNames(), getMatchingTags()));
    }

    @Override
    protected void doStop() {

    }
}
