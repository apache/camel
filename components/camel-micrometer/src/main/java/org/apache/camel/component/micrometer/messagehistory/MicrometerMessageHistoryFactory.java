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
package org.apache.camel.component.micrometer.messagehistory;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.MessageHistory;
import org.apache.camel.NamedNode;
import org.apache.camel.NonManagedService;
import org.apache.camel.StaticService;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * A factory to setup and use {@link MicrometerMessageHistory} as message history implementation.
 */
public class MicrometerMessageHistoryFactory extends ServiceSupport implements CamelContextAware, StaticService, NonManagedService, MessageHistoryFactory {

    private CamelContext camelContext;
    private MeterRegistry meterRegistry;
    private boolean prettyPrint = true;
    private TimeUnit durationUnit = TimeUnit.MILLISECONDS;
    private String prefix = MicrometerConstants.HEADER_PREFIX;
    private String namePattern = "##prefix##.##name##.##routeId##.##id##.##type##";

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    /**
     * To use a specific {@link MeterRegistry} instance.
     * <p/>
     * If no instance has been configured, then Camel will create a shared instance to be used.
     */
    public void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
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

    public TimeUnit getDurationUnit() {
        return durationUnit;
    }

    /**
     * Sets the time unit to use for timing the duration of processing a message in the route
     */
    public void setDurationUnit(TimeUnit durationUnit) {
        this.durationUnit = durationUnit;
    }

    public String getNamePattern() {
        return namePattern;
    }

    public void setNamePattern(String namePattern) {
        this.namePattern = namePattern;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    @Deprecated
    public MessageHistory newMessageHistory(String routeId, NamedNode namedNode, Date date) {
        return newMessageHistory(routeId, namedNode, date.getTime());
    }

    @Override
    public MessageHistory newMessageHistory(String routeId, NamedNode namedNode, long timestamp) {
        return new MicrometerMessageHistory(meterRegistry, routeId, namedNode,
                createName("history", routeId, namedNode.getId()),
                timestamp);
    }

    private String createName(String type, String routeId, String id) {
        String name = camelContext.getManagementName() != null ? camelContext.getManagementName() : camelContext.getName();

        String answer = namePattern;
        answer = answer.replaceFirst("##prefix##", name);
        answer = answer.replaceFirst("##name##", name);
        answer = answer.replaceFirst("##routeId##", routeId);
        answer = answer.replaceFirst("##id##", id);
        answer = answer.replaceFirst("##type##", type);
        return answer;
    }

    @Override
    protected void doStart() {
        MicrometerMessageHistoryService messageHistoryService;
        try {
            messageHistoryService = camelContext.hasService(MicrometerMessageHistoryService.class);
            if (messageHistoryService == null) {
                messageHistoryService = new MicrometerMessageHistoryService();
                messageHistoryService.setMeterRegistry(getMeterRegistry());
                messageHistoryService.setPrettyPrint(isPrettyPrint());
                messageHistoryService.setDurationUnit(getDurationUnit());
                camelContext.addService(messageHistoryService);
            }
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }

        // use metrics registry from service if not explicit configured
        if (meterRegistry == null) {
            meterRegistry = messageHistoryService.getMeterRegistry();
        }

        ObjectHelper.notNull(meterRegistry, "meterRegistry", this);
    }

    @Override
    protected void doStop() {
        // noop
    }
}
