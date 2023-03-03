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
package org.apache.camel.component.micrometer.routepolicy;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;

/**
 * A {@link org.apache.camel.spi.RoutePolicyFactory} to plugin and use metrics for gathering route utilization
 * statistics
 */
public class MicrometerRoutePolicyFactory implements RoutePolicyFactory {

    private MeterRegistry meterRegistry;
    private boolean prettyPrint = true;
    private TimeUnit durationUnit = TimeUnit.MILLISECONDS;
    private MicrometerRoutePolicyNamingStrategy namingStrategy = MicrometerRoutePolicyNamingStrategy.DEFAULT;
    private MicrometerRoutePolicyConfiguration policyConfiguration = MicrometerRoutePolicyConfiguration.DEFAULT;

    /**
     * To use a specific {@link io.micrometer.core.instrument.MeterRegistry} instance.
     * <p/>
     * If no instance has been configured, then Camel will create a shared instance to be used.
     */
    public void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    /**
     * Whether to use pretty print when outputting JSon
     */
    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    /**
     * Sets the time unit to use for requests per unit (eg requests per second)
     */
    public TimeUnit getDurationUnit() {
        return durationUnit;
    }

    /**
     * Sets the time unit to use for timing the duration of processing a message in the route
     */
    public void setDurationUnit(TimeUnit durationUnit) {
        this.durationUnit = durationUnit;
    }

    public MicrometerRoutePolicyNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(MicrometerRoutePolicyNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    public MicrometerRoutePolicyConfiguration getPolicyConfiguration() {
        return policyConfiguration;
    }

    public void setPolicyConfiguration(MicrometerRoutePolicyConfiguration policyConfiguration) {
        this.policyConfiguration = policyConfiguration;
    }

    @Override
    public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, NamedNode routeDefinition) {
        MicrometerRoutePolicy answer = new MicrometerRoutePolicy();
        answer.setMeterRegistry(getMeterRegistry());
        answer.setPrettyPrint(isPrettyPrint());
        answer.setDurationUnit(getDurationUnit());
        answer.setNamingStrategy(getNamingStrategy());
        answer.setConfiguration(getPolicyConfiguration());
        return answer;
    }

}
