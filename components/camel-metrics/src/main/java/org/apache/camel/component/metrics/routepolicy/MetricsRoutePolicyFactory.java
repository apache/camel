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

import com.codahale.metrics.MetricRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;

/**
 * A {@link org.apache.camel.spi.RoutePolicyFactory} to plugin and use metrics for gathering route utilization statistics
 */
public class MetricsRoutePolicyFactory implements RoutePolicyFactory {

    private MetricRegistry metricsRegistry;
    private boolean useJmx;
    private String jmxDomain = "org.apache.camel.metrics";
    private boolean prettyPrint;
    private TimeUnit rateUnit = TimeUnit.SECONDS;
    private TimeUnit durationUnit = TimeUnit.MILLISECONDS;

    /**
     * To use a specific {@link com.codahale.metrics.MetricRegistry} instance.
     * <p/>
     * If no instance has been configured, then Camel will create a shared instance to be used.
     */
    public void setMetricsRegistry(MetricRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
    }

    public MetricRegistry getMetricsRegistry() {
        return metricsRegistry;
    }

    public boolean isUseJmx() {
        return useJmx;
    }

    /**
     * Whether to use JMX reported to enlist JMX MBeans with the metrics statistics.
     */
    public void setUseJmx(boolean useJmx) {
        this.useJmx = useJmx;
    }

    public String getJmxDomain() {
        return jmxDomain;
    }

    /**
     * The JMX domain name to use for the enlisted JMX MBeans.
     */
    public void setJmxDomain(String jmxDomain) {
        this.jmxDomain = jmxDomain;
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

    public TimeUnit getRateUnit() {
        return rateUnit;
    }

    /**
     * Sets the time unit to use for requests per unit (eg requests per second)
     */
    public void setRateUnit(TimeUnit rateUnit) {
        this.rateUnit = rateUnit;
    }

    public TimeUnit getDurationUnit() {
        return durationUnit;
    }

    /**
     * Sets the time unit to use for timing the duration of processing a message in the route
     */
    public void setDurationUnit(TimeUnit durationUnit) {
        this.durationUnit = durationUnit;
    }

    @Override
    public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, RouteDefinition routeDefinition) {
        MetricsRoutePolicy answer = new MetricsRoutePolicy();
        answer.setMetricsRegistry(getMetricsRegistry());
        answer.setUseJmx(isUseJmx());
        answer.setJmxDomain(getJmxDomain());
        answer.setPrettyPrint(isPrettyPrint());
        answer.setRateUnit(getRateUnit());
        answer.setDurationUnit(getDurationUnit());
        return answer;
    }

}
