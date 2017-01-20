/**
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
package org.apache.camel.component.metrics;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages metrics endpoints.
 */
public class MetricsComponent extends UriEndpointComponent {

    public static final String METRIC_REGISTRY_NAME = "metricRegistry";
    public static final MetricsType DEFAULT_METRICS_TYPE = MetricsType.METER;
    public static final long DEFAULT_REPORTING_INTERVAL_SECONDS = 60L;

    private static final Logger LOG = LoggerFactory.getLogger(MetricsComponent.class);

    @Metadata(label = "advanced")
    private MetricRegistry metricRegistry;

    public MetricsComponent() {
        super(MetricsEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (metricRegistry == null) {
            Registry camelRegistry = getCamelContext().getRegistry();
            metricRegistry = getOrCreateMetricRegistry(camelRegistry, METRIC_REGISTRY_NAME);
        }
        String metricsName = getMetricsName(remaining);
        MetricsType metricsType = getMetricsType(remaining);

        LOG.debug("Metrics type: {}; name: {}", metricsType, metricsName);
        Endpoint endpoint = new MetricsEndpoint(uri, this, metricRegistry, metricsType, metricsName);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    String getMetricsName(String remaining) {
        String name = ObjectHelper.after(remaining, ":");
        return name == null ? remaining : name;
    }

    MetricsType getMetricsType(String remaining) {
        String name = ObjectHelper.before(remaining, ":");
        MetricsType type;
        if (name == null) {
            type = DEFAULT_METRICS_TYPE;
        } else {
            type = MetricsType.getByName(name);
        }
        if (type == null) {
            throw new RuntimeCamelException("Unknown metrics type \"" + name + "\"");
        }
        return type;
    }

    MetricRegistry getOrCreateMetricRegistry(Registry camelRegistry, String registryName) {
        LOG.debug("Looking up MetricRegistry from Camel Registry for name \"{}\"", registryName);
        MetricRegistry result = getMetricRegistryFromCamelRegistry(camelRegistry, registryName);
        if (result == null) {
            LOG.debug("MetricRegistry not found from Camel Registry for name \"{}\"", registryName);
            LOG.info("Creating new default MetricRegistry");
            result = createMetricRegistry();
        }
        return result;
    }

    MetricRegistry getMetricRegistryFromCamelRegistry(Registry camelRegistry, String registryName) {
        MetricRegistry registry = camelRegistry.lookupByNameAndType(registryName, MetricRegistry.class);
        if (registry != null) {
            return registry;
        } else {
            Set<MetricRegistry> registries = camelRegistry.findByType(MetricRegistry.class);
            if (registries.size() == 1) {
                return registries.iterator().next();
            }
        }
        return null;
    }

    MetricRegistry createMetricRegistry() {
        MetricRegistry registry = new MetricRegistry();
        final Slf4jReporter reporter = Slf4jReporter.forRegistry(registry)
                .outputTo(LOG)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .withLoggingLevel(Slf4jReporter.LoggingLevel.DEBUG)
                .build();
        reporter.start(DEFAULT_REPORTING_INTERVAL_SECONDS, TimeUnit.SECONDS);
        return registry;
    }

    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    /**
     * To use a custom configured MetricRegistry.
     */
    public void setMetricRegistry(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }
}
