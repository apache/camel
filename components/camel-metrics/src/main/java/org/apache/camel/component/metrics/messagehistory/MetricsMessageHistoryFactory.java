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
package org.apache.camel.component.metrics.messagehistory;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.MessageHistory;
import org.apache.camel.NamedNode;
import org.apache.camel.NonManagedService;
import org.apache.camel.StaticService;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * A factory to setup and use {@link MetricsMessageHistory} as message history implementation.
 */
public class MetricsMessageHistoryFactory extends ServiceSupport implements CamelContextAware, StaticService, NonManagedService, MessageHistoryFactory {

    private CamelContext camelContext;
    private MetricsMessageHistoryService messageHistoryService;
    private MetricRegistry metricsRegistry;
    private boolean useJmx;
    private String jmxDomain = "org.apache.camel.metrics";
    private boolean prettyPrint;
    private TimeUnit rateUnit = TimeUnit.SECONDS;
    private TimeUnit durationUnit = TimeUnit.MILLISECONDS;
    private String namePattern = "##name##.##routeId##.##id##.##type##";

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public MetricRegistry getMetricsRegistry() {
        return metricsRegistry;
    }

    /**
     * To use a specific {@link com.codahale.metrics.MetricRegistry} instance.
     * <p/>
     * If no instance has been configured, then Camel will create a shared instance to be used.
     */
    public void setMetricsRegistry(MetricRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
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
    @Deprecated
    public MessageHistory newMessageHistory(String routeId, NamedNode namedNode, Date date) {
        return newMessageHistory(routeId, namedNode, date.getTime());
    }

    @Override
    public MessageHistory newMessageHistory(String routeId, NamedNode namedNode, long timestamp) {
        Timer timer = metricsRegistry.timer(createName("history", routeId, namedNode.getId()));
        return new MetricsMessageHistory(routeId, namedNode, timer, timestamp);
    }

    private String createName(String type, String routeId, String id) {
        String name = camelContext.getManagementName() != null ? camelContext.getManagementName() : camelContext.getName();

        String answer = namePattern;
        answer = answer.replaceFirst("##name##", name);
        answer = answer.replaceFirst("##routeId##", routeId);
        answer = answer.replaceFirst("##id##", id);
        answer = answer.replaceFirst("##type##", type);
        return answer;
    }

    @Override
    protected void doStart() throws Exception {
        try {
            messageHistoryService = camelContext.hasService(MetricsMessageHistoryService.class);
            if (messageHistoryService == null) {
                messageHistoryService = new MetricsMessageHistoryService();
                messageHistoryService.setMetricsRegistry(getMetricsRegistry());
                messageHistoryService.setUseJmx(isUseJmx());
                messageHistoryService.setJmxDomain(getJmxDomain());
                messageHistoryService.setPrettyPrint(isPrettyPrint());
                messageHistoryService.setRateUnit(getRateUnit());
                messageHistoryService.setDurationUnit(getDurationUnit());
                camelContext.addService(messageHistoryService);
            }
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }

        // use metrics registry from service if not explicit configured
        if (metricsRegistry == null) {
            metricsRegistry = messageHistoryService.getMetricsRegistry();
        }

        ObjectHelper.notNull(metricsRegistry, "metricsRegistry", this);
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
