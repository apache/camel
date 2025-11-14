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
package org.apache.camel.opentelemetry.metrics.routepolicy;

import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.metrics.Meter;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.NamedNode;
import org.apache.camel.NonManagedService;
import org.apache.camel.StaticService;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.support.service.ServiceSupport;

/**
 * A {@link RoutePolicyFactory} to plugin and use metrics for gathering route utilization statistics
 */
public class OpenTelemetryRoutePolicyFactory extends ServiceSupport
        implements RoutePolicyFactory, CamelContextAware, NonManagedService, StaticService {

    private CamelContext camelContext;
    private Meter meter;
    private RouteMetric contextMetric;
    private OpenTelemetryRoutePolicyNamingStrategy namingStrategy = OpenTelemetryRoutePolicyNamingStrategy.DEFAULT;
    private OpenTelemetryRoutePolicyConfiguration policyConfiguration = new OpenTelemetryRoutePolicyConfiguration();
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public void setMeter(Meter meter) {
        this.meter = meter;
    }

    public Meter getMeter() {
        return meter;
    }

    public OpenTelemetryRoutePolicyNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(OpenTelemetryRoutePolicyNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    public OpenTelemetryRoutePolicyConfiguration getPolicyConfiguration() {
        return policyConfiguration;
    }

    public void setPolicyConfiguration(OpenTelemetryRoutePolicyConfiguration policyConfiguration) {
        this.policyConfiguration = policyConfiguration;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    @Override
    public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, NamedNode routeDefinition) {
        OpenTelemetryRoutePolicy routePolicy = new OpenTelemetryRoutePolicy(this);
        routePolicy.setNamingStrategy(getNamingStrategy());
        routePolicy.setConfiguration(getPolicyConfiguration());
        routePolicy.setTimeUnit(getTimeUnit());
        routePolicy.setMeter(meter);
        return routePolicy;
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        if (contextMetric != null) {
            contextMetric.remove();
            contextMetric = null;
        }
    }
}
