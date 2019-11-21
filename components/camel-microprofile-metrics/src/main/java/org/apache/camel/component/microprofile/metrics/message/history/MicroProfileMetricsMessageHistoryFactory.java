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
package org.apache.camel.component.microprofile.metrics.message.history;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.MessageHistory;
import org.apache.camel.NamedNode;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StaticService;
import org.apache.camel.component.microprofile.metrics.MicroProfileMetricsHelper;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.eclipse.microprofile.metrics.MetricRegistry;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_TAG;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.DEFAULT_CAMEL_MESSAGE_HISTORY_METRIC_NAME;

public class MicroProfileMetricsMessageHistoryFactory extends ServiceSupport implements CamelContextAware, StaticService, NonManagedService, MessageHistoryFactory {

    private CamelContext camelContext;
    private MetricRegistry metricRegistry;
    private boolean copyMessage;
    private String nodePattern;
    private MicroProfileMetricsMessageHistoryNamingStrategy namingStrategy = MicroProfileMetricsMessageHistoryNamingStrategy.DEFAULT;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    public void setMetricRegistry(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    public MicroProfileMetricsMessageHistoryNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(MicroProfileMetricsMessageHistoryNamingStrategy namingStrategy) {
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
    public MessageHistory newMessageHistory(String routeId, NamedNode namedNode, long timestamp, Exchange exchange) {
        if (nodePattern != null) {
            String name = namedNode.getShortName();
            String[] parts = nodePattern.split(",");
            for (String part : parts) {
                boolean match = PatternHelper.matchPattern(name, part);
                if (!match) {
                    return null;
                }
            }
        }

        Message msg = null;
        if (copyMessage) {
            msg = exchange.getMessage().copy();
        }

        Route route = camelContext.getRoute(routeId);
        if (route != null) {
            return new MicroProfileMetricsMessageHistory(getMetricRegistry(), route, namedNode, getNamingStrategy(), timestamp, msg);
        } else {
            return null;
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (metricRegistry == null) {
            metricRegistry = MicroProfileMetricsHelper.getMetricRegistry(camelContext);
        }

        try {
            MicroProfileMetricsMessageHistoryService service = camelContext.hasService(MicroProfileMetricsMessageHistoryService.class);
            if (service == null) {
                service = new MicroProfileMetricsMessageHistoryService();
                service.setMetricRegistry(getMetricRegistry());
                camelContext.addService(service);
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    @Override
    protected void doStop() {
        MicroProfileMetricsHelper.removeMetricsFromRegistry(metricRegistry, (metricID, metric) -> {
            if (metricID.getName().equals(DEFAULT_CAMEL_MESSAGE_HISTORY_METRIC_NAME)) {
                Map<String, String> tags = metricID.getTags();
                if (tags.containsKey(CAMEL_CONTEXT_TAG)) {
                    return tags.get(CAMEL_CONTEXT_TAG).equals(camelContext.getName());
                }
            }
            return false;
        });
    }
}
