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

import org.apache.camel.CamelContext;
import org.apache.camel.component.microprofile.metrics.event.notifier.AbstractMicroProfileMetricsEventNotifier;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.RouteEvent;
import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import static org.apache.camel.spi.CamelEvent.Type.RouteAdded;
import static org.apache.camel.spi.CamelEvent.Type.RouteRemoved;
import static org.apache.camel.spi.CamelEvent.Type.RouteStarted;
import static org.apache.camel.spi.CamelEvent.Type.RouteStopped;

public class MicroProfileMetricsRouteEventNotifier extends AbstractMicroProfileMetricsEventNotifier<RouteEvent> {

    private ConcurrentGauge routesAdded;
    private ConcurrentGauge routesRunning;
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
        routesAdded = metricRegistry.concurrentGauge(namingStrategy.getRouteAddedName(), tags);
        routesRunning = metricRegistry.concurrentGauge(namingStrategy.getRouteRunningName(), tags);
    }

    @Override
    public void notify(CamelEvent event) throws Exception {
        if (routesAdded == null || routesRunning == null) {
            return;
        }

        if (event.getType().equals(RouteAdded)) {
            routesAdded.inc();
        } else if (event.getType().equals(RouteRemoved)) {
            routesAdded.dec();
        } else if (event.getType().equals(RouteStarted)) {
            routesRunning.inc();
        } else if (event.getType().equals(RouteStopped)) {
            routesRunning.dec();
        }
    }

    public MicroProfileMetricsRouteEventNotifierNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(MicroProfileMetricsRouteEventNotifierNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }
}
