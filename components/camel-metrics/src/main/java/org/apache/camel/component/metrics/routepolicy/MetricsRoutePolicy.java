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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.impl.RoutePolicySupport;
import org.apache.camel.util.ObjectHelper;

/**
 * A {@link org.apache.camel.spi.RoutePolicy} which gathers statistics and reports them using {@link com.codahale.metrics.MetricRegistry}.
 * <p/>
 * The metrics is reported in JMX by default, but this can be configured.
 */
public class MetricsRoutePolicy extends RoutePolicySupport {

    // TODO: allow to configure which counters/meters/timers to capture
    // TODO: allow to configur the reporer and jmx domain etc on MetricsRegistryService
    // TODO: RoutePolicyFactory to make this configurable once and apply automatic for all routes
    // TODO: allow to lookup and get hold of com.codahale.metrics.MetricRegistry from java api

    private MetricsRegistryService registry;
    private final ConcurrentMap<Route, MetricsStatistics> statistics = new ConcurrentHashMap<Route, MetricsStatistics>();
    private Route route;

    private final static class MetricsStatistics {
        private Counter total;
        private Counter inflight;
        private Meter requests;
        private Timer responses;

        private MetricsStatistics(Counter total, Counter inflight, Meter requests, Timer responses) {
            this.total = total;
            this.inflight = inflight;
            this.requests = requests;
            this.responses = responses;
        }

        public void onExchangeBegin(Exchange exchange) {
            total.inc();
            inflight.inc();
            requests.mark();

            Timer.Context context = responses.time();
            exchange.setProperty("MetricsRoutePolicy", context);
        }

        public void onExchangeDone(Exchange exchange) {
            inflight.dec();

            Timer.Context context = exchange.getProperty("MetricsRoutePolicy", Timer.Context.class);
            if (context != null) {
                context.stop();
            }
        }
    }

    @Override
    public void onInit(Route route) {
        super.onInit(route);

        this.route = route;
        try {
            registry = route.getRouteContext().getCamelContext().hasServiceByType(MetricsRegistryService.class);
            if (registry == null) {
                registry = new MetricsRegistryService();
                route.getRouteContext().getCamelContext().addService(registry);
            }
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }

        MetricsStatistics stats = statistics.get(route);
        if (stats == null) {
            Counter total = registry.getRegistry().counter(createName("total"));
            Counter inflight = registry.getRegistry().counter(createName("inflight"));
            Meter requests = registry.getRegistry().meter(createName("requests"));
            Timer responses = registry.getRegistry().timer(createName("responses"));
            stats = new MetricsStatistics(total, inflight, requests, responses);
            statistics.putIfAbsent(route, stats);
        }
    }

    private String createName(String type) {
        return route.getRouteContext().getCamelContext().getManagementName() + "-" + route.getId() + "-" + type;
    }

    @Override
    public void onExchangeBegin(Route route, Exchange exchange) {
        MetricsStatistics stats = statistics.get(route);
        if (stats != null) {
            stats.onExchangeBegin(exchange);
        }
    }

    @Override
    public void onExchangeDone(Route route, Exchange exchange) {
        MetricsStatistics stats = statistics.get(route);
        if (stats != null) {
            stats.onExchangeDone(exchange);
        }
    }

}
