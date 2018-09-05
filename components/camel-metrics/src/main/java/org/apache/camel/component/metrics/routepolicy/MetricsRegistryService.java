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
package org.apache.camel.component.metrics.routepolicy;

import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.json.MetricsModule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.StaticService;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.metrics.MetricsComponent;
import org.apache.camel.spi.ManagementAgent;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * Service holding the {@link MetricRegistry} which registers all metrics.
 */
@ManagedResource(description = "MetricsRegistry")
public final class MetricsRegistryService extends ServiceSupport implements CamelContextAware, StaticService, MetricsRegistryMBean {

    private CamelContext camelContext;
    private MetricRegistry metricsRegistry;
    private JmxReporter reporter;
    private boolean useJmx;
    private String jmxDomain = "org.apache.camel.metrics";
    private boolean prettyPrint;
    private TimeUnit rateUnit = TimeUnit.SECONDS;
    private TimeUnit durationUnit = TimeUnit.MILLISECONDS;
    private transient ObjectMapper mapper;
    private transient ObjectMapper secondsMapper;

    public MetricRegistry getMetricsRegistry() {
        return metricsRegistry;
    }

    public void setMetricsRegistry(MetricRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public boolean isUseJmx() {
        return useJmx;
    }

    public void setUseJmx(boolean useJmx) {
        this.useJmx = useJmx;
    }

    public String getJmxDomain() {
        return jmxDomain;
    }

    public void setJmxDomain(String jmxDomain) {
        this.jmxDomain = jmxDomain;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public TimeUnit getRateUnit() {
        return rateUnit;
    }

    public void setRateUnit(TimeUnit rateUnit) {
        this.rateUnit = rateUnit;
    }

    public TimeUnit getDurationUnit() {
        return durationUnit;
    }

    public void setDurationUnit(TimeUnit durationUnit) {
        this.durationUnit = durationUnit;
    }

    @Override
    protected void doStart() throws Exception {
        if (metricsRegistry == null) {
            Registry camelRegistry = getCamelContext().getRegistry();
            metricsRegistry = camelRegistry.lookupByNameAndType(MetricsComponent.METRIC_REGISTRY_NAME, MetricRegistry.class);
            // create a new metricsRegistry by default
            if (metricsRegistry == null) {
                metricsRegistry = new MetricRegistry();
            }
        }

        if (useJmx) {
            ManagementAgent agent = getCamelContext().getManagementStrategy().getManagementAgent();
            if (agent != null) {
                MBeanServer server = agent.getMBeanServer();
                if (server != null) {
                    reporter = JmxReporter.forRegistry(metricsRegistry).registerWith(server).inDomain(jmxDomain).build();
                    reporter.start();
                }
            } else {
                throw new IllegalStateException("CamelContext has not enabled JMX");
            }
        }

        // json mapper
        this.mapper = new ObjectMapper().registerModule(new MetricsModule(getRateUnit(), getDurationUnit(), false));
        if (getRateUnit() == TimeUnit.SECONDS && getDurationUnit() == TimeUnit.SECONDS) {
            // they both use same units so reuse
            this.secondsMapper = this.mapper;
        } else {
            this.secondsMapper = new ObjectMapper().registerModule(new MetricsModule(TimeUnit.SECONDS, TimeUnit.SECONDS, false));
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (reporter != null) {
            reporter.stop();
            reporter = null;
        }
    }

    @Override
    public String dumpStatisticsAsJson() {
        ObjectWriter writer = mapper.writer();
        if (isPrettyPrint()) {
            writer = writer.withDefaultPrettyPrinter();
        }
        try {
            return writer.writeValueAsString(getMetricsRegistry());
        } catch (JsonProcessingException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    public String dumpStatisticsAsJsonTimeUnitSeconds() {
        ObjectWriter writer = secondsMapper.writer();
        if (isPrettyPrint()) {
            writer = writer.withDefaultPrettyPrinter();
        }
        try {
            return writer.writeValueAsString(getMetricsRegistry());
        } catch (JsonProcessingException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

}
