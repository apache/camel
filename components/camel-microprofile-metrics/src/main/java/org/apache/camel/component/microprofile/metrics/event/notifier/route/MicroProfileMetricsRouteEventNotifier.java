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
package org.apache.camel.component.microprofile.metrics.event.notifier.route;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.component.microprofile.metrics.MicroProfileMetricsHelper;
import org.apache.camel.component.microprofile.metrics.event.notifier.AbstractMicroProfileMetricsEventNotifier;
import org.apache.camel.component.microprofile.metrics.gauge.AtomicIntegerGauge;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.RouteEvent;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetadataBuilder;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Tag;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_TAG;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.ROUTES_ADDED_DESCRIPTION;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.ROUTES_ADDED_DISPLAY_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.ROUTES_RUNNING_DESCRIPTION;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.ROUTES_RUNNING_DISPLAY_NAME;
import static org.apache.camel.spi.CamelEvent.Type.RouteAdded;
import static org.apache.camel.spi.CamelEvent.Type.RouteRemoved;
import static org.apache.camel.spi.CamelEvent.Type.RouteStarted;
import static org.apache.camel.spi.CamelEvent.Type.RouteStopped;

public class MicroProfileMetricsRouteEventNotifier extends AbstractMicroProfileMetricsEventNotifier<RouteEvent> {

    private AtomicIntegerGauge routesAdded = new AtomicIntegerGauge();
    private AtomicIntegerGauge routesRunning = new AtomicIntegerGauge();
    private MicroProfileMetricsRouteEventNotifierNamingStrategy namingStrategy = MicroProfileMetricsRouteEventNotifierNamingStrategy.DEFAULT;

    public MicroProfileMetricsRouteEventNotifier() {
        super(RouteEvent.class);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        CamelContext camelContext = getCamelContext();
        MetricRegistry metricRegistry = getMetricRegistry();
        Tag[] tags = namingStrategy.getTags(camelContext);

        Metadata routesAddedMetadata = new MetadataBuilder()
            .withName(namingStrategy.getRouteAddedName())
            .withDisplayName(ROUTES_ADDED_DISPLAY_NAME)
            .withDescription(ROUTES_ADDED_DESCRIPTION)
            .withType(MetricType.GAUGE)
            .build();

        metricRegistry.register(routesAddedMetadata, routesAdded, tags);

        Metadata routesRunningMetadata = new MetadataBuilder()
            .withName(namingStrategy.getRouteRunningName())
            .withDisplayName(ROUTES_RUNNING_DISPLAY_NAME)
            .withDescription(ROUTES_RUNNING_DESCRIPTION)
            .withType(MetricType.GAUGE)
            .build();
        metricRegistry.register(routesRunningMetadata, routesRunning, tags);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        MicroProfileMetricsHelper.removeMetricsFromRegistry(getMetricRegistry(), (metricID, metric) -> {
            String name = metricID.getName();
            Map<String, String> tags = metricID.getTags();
            if (name.equals(namingStrategy.getRouteRunningName()) || name.equals(namingStrategy.getRouteAddedName())) {
                if (tags.containsKey(CAMEL_CONTEXT_TAG)) {
                    return tags.get(CAMEL_CONTEXT_TAG).equals(getCamelContext().getName());
                }
            }
            return false;
        });
    }

    @Override
    public void notify(CamelEvent event) throws Exception {
        if (routesAdded == null || routesRunning == null) {
            return;
        }

        if (event.getType().equals(RouteAdded)) {
            routesAdded.increment();
        } else if (event.getType().equals(RouteRemoved)) {
            routesAdded.decrement();
        } else if (event.getType().equals(RouteStarted)) {
            routesRunning.increment();
        } else if (event.getType().equals(RouteStopped)) {
            routesRunning.decrement();
        }
    }

    public MicroProfileMetricsRouteEventNotifierNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(MicroProfileMetricsRouteEventNotifierNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }
}
