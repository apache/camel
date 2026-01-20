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
package org.apache.camel.opentelemetry.metrics.messagehistory;

import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.MessageHistory;
import org.apache.camel.NamedNode;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.service.ServiceSupport;

public class OpenTelemetryMessageHistoryFactory extends ServiceSupport
        implements NonManagedService, MessageHistoryFactory {

    private CamelContext camelContext;
    private Meter meter;
    private boolean copyMessage;
    private String nodePattern;
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    private OpenTelemetryHistoryNamingStrategy namingStrategy = OpenTelemetryHistoryNamingStrategy.DEFAULT;
    private LongHistogram timer;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public Meter getMeter() {
        return meter;
    }

    /**
     * Sets the OpenTelemetry meter to use for recording metrics.
     */
    public void setMeter(Meter meter) {
        this.meter = meter;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    /**
     * Sets the time unit to use for timing the duration of processing a message in the route.
     */
    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public OpenTelemetryHistoryNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    /**
     * Sets the naming strategy for message history meter names.
     */
    public void setNamingStrategy(OpenTelemetryHistoryNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    @Override
    public boolean isCopyMessage() {
        return copyMessage;
    }

    @Override
    public void setCopyMessage(boolean copyMessage) {
        this.copyMessage = copyMessage;
    }

    @Override
    public String getNodePattern() {
        return nodePattern;
    }

    @Override
    public void setNodePattern(String nodePattern) {
        this.nodePattern = nodePattern;
    }

    @Override
    public MessageHistory newMessageHistory(String routeId, NamedNode namedNode, Exchange exchange) {
        if (nodePattern != null) {
            String name = namedNode.getShortName();
            String[] parts = nodePattern.split(",");
            boolean match = false;
            for (String part : parts) {
                if (PatternHelper.matchPattern(name, part)) {
                    match = true;
                    break;
                }
            }
            // no match on any part
            if (!match) {
                return null;
            }
        }

        Message msg = null;
        if (copyMessage) {
            msg = exchange.getMessage().copy();
        }

        Route route = camelContext.getRoute(routeId);
        if (route != null) {
            return new OpenTelemetryMessageHistory(timer, getTimeUnit(), route, namedNode, getNamingStrategy(), msg);
        } else {
            return null;
        }
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        if (meter == null) {
            this.meter = CamelContextHelper.findSingleByType(getCamelContext(), Meter.class);
        }
        if (meter == null) {
            this.meter = GlobalOpenTelemetry.get().getMeter("camel");
        }
        if (meter == null) {
            throw new RuntimeCamelException("Could not find any OpenTelemetry meter!");
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.timer = meter
                .histogramBuilder(getNamingStrategy().getName())
                .ofLongs()
                .setDescription("Node performance metrics")
                .setUnit(timeUnit.name().toLowerCase())
                .build();
    }
}
