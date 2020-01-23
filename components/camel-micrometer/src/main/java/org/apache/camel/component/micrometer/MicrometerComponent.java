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
package org.apache.camel.component.micrometer;

import java.util.Map;
import java.util.stream.Stream;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages Micrometer endpoints.
 */
@Component("micrometer")
public class MicrometerComponent extends DefaultComponent {

    public static final Meter.Type DEFAULT_METER_TYPE = Meter.Type.COUNTER;

    private static final Logger LOG = LoggerFactory.getLogger(MicrometerComponent.class);

    @Metadata(label = "advanced")
    private MeterRegistry metricsRegistry;

    public MicrometerComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (metricsRegistry == null) {
            Registry camelRegistry = getCamelContext().getRegistry();
            metricsRegistry = MicrometerUtils.getOrCreateMeterRegistry(camelRegistry, MicrometerConstants.METRICS_REGISTRY_NAME);
        }
        String metricsName = getMetricsName(remaining);
        Meter.Type metricsType = getMetricsType(remaining);
        Iterable<Tag> tags = getMetricsTag(parameters);

        LOG.debug("Metrics type: {}; name: {}; tags: {}", metricsType, metricsName, tags);
        Endpoint endpoint = new MicrometerEndpoint(uri, this, metricsRegistry, metricsType, metricsName, tags);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    String getMetricsName(String remaining) {
        String name = StringHelper.after(remaining, ":");
        return name == null ? remaining : name;
    }

    Meter.Type getMetricsType(String remaining) {
        String type = StringHelper.before(remaining, ":");
        return type == null
                ? DEFAULT_METER_TYPE
                : MicrometerUtils.getByName(type);
    }

    Iterable<Tag> getMetricsTag(Map<String, Object> parameters) {
        String tagsString = getAndRemoveParameter(parameters, "tags", String.class, "");
        if (tagsString != null && !tagsString.isEmpty()) {
            String[] tagStrings = tagsString.split("\\s*,\\s*");
            return Stream.of(tagStrings)
                    .map(s -> Tags.of(s.split("\\s*=\\s*")))
                    .reduce(Tags.empty(), Tags::and);
        }
        return Tags.empty();
    }

    public MeterRegistry getMetricsRegistry() {
        return metricsRegistry;
    }

    /**
     * To use a custom configured MetricRegistry.
     */
    public void setMetricsRegistry(MeterRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
    }
}
