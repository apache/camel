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
package org.apache.camel.component.micrometer.prometheus;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.StaticService;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.micrometer.eventnotifier.MicrometerExchangeEventNotifier;
import org.apache.camel.component.micrometer.eventnotifier.MicrometerRouteEventNotifier;
import org.apache.camel.component.micrometer.messagehistory.MicrometerMessageHistoryFactory;
import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyFactory;
import org.apache.camel.spi.CamelMetricsService;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JdkService("micrometer-prometheus")
@Configurer
@ManagedResource(description = "Micrometer Metrics Prometheus")
public class MicrometerPrometheus extends ServiceSupport implements CamelMetricsService, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(MicrometerPrometheus.class);

    // TODO: option to configure prometheus
    // TODO: context-path / port for http scrape service
    // TODO: use platform-http-main
    // TODO: dev console

    private CamelContext camelContext;
    private final PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    @Metadata(defaultValue = "true")
    private boolean enableRoutePolicy = true;
    @Metadata(defaultValue = "false")
    private boolean enableMessageHistory;
    @Metadata(defaultValue = "true")
    private boolean enableExchangeEventNotifier = true;
    @Metadata(defaultValue = "true")
    private boolean enableRouteEventNotifier = true;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public boolean isEnableRoutePolicy() {
        return enableRoutePolicy;
    }

    /**
     * Set whether to enable the MicrometerRoutePolicyFactory for capturing metrics on route processing times.
     */
    public void setEnableRoutePolicy(boolean enableRoutePolicy) {
        this.enableRoutePolicy = enableRoutePolicy;
    }

    public boolean isEnableMessageHistory() {
        return enableMessageHistory;
    }

    /**
     * Set whether to enable the MicrometerMessageHistoryFactory for capturing metrics on individual route node
     * processing times.
     *
     * Depending on the number of configured route nodes, there is the potential to create a large volume of metrics.
     * Therefore, this option is disabled by default.
     */
    public void setEnableMessageHistory(boolean enableMessageHistory) {
        this.enableMessageHistory = enableMessageHistory;
    }

    public boolean isEnableExchangeEventNotifier() {
        return enableExchangeEventNotifier;
    }

    /**
     * Set whether to enable the MicrometerExchangeEventNotifier for capturing metrics on exchange processing times.
     */
    public void setEnableExchangeEventNotifier(boolean enableExchangeEventNotifier) {
        this.enableExchangeEventNotifier = enableExchangeEventNotifier;
    }

    public boolean isEnableRouteEventNotifier() {
        return enableRouteEventNotifier;
    }

    /**
     * Set whether to enable the MicrometerRouteEventNotifier for capturing metrics on the total number of routes and
     * total number of routes running.
     */
    public void setEnableRouteEventNotifier(boolean enableRouteEventNotifier) {
        this.enableRouteEventNotifier = enableRouteEventNotifier;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (isEnableRoutePolicy()) {
            MicrometerRoutePolicyFactory factory = new MicrometerRoutePolicyFactory();
            factory.setMeterRegistry(meterRegistry);
            camelContext.addRoutePolicyFactory(factory);
        }

        ManagementStrategy managementStrategy = camelContext.getManagementStrategy();
        if (isEnableExchangeEventNotifier()) {
            MicrometerExchangeEventNotifier notifier = new MicrometerExchangeEventNotifier();
            notifier.setMeterRegistry(meterRegistry);
            managementStrategy.addEventNotifier(notifier);
        }

        if (isEnableRouteEventNotifier()) {
            MicrometerRouteEventNotifier notifier = new MicrometerRouteEventNotifier();
            notifier.setMeterRegistry(meterRegistry);
            managementStrategy.addEventNotifier(notifier);
        }

        if (isEnableMessageHistory()) {
            if (!camelContext.isMessageHistory()) {
                camelContext.setMessageHistory(true);
            }
            MicrometerMessageHistoryFactory factory = new MicrometerMessageHistoryFactory();
            factory.setMeterRegistry(meterRegistry);
            camelContext.setMessageHistoryFactory(factory);
        }

        LOG.info("MicrometerPrometheus enabled");
    }
}
