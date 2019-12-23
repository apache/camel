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
package org.apache.camel.component.microprofile.metrics.route.policy;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.microprofile.metrics.MicroProfileMetricsExchangeRecorder;
import org.apache.camel.component.microprofile.metrics.MicroProfileMetricsHelper;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.Timer.Context;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_TAG;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.DEFAULT_CAMEL_ROUTE_POLICY_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.PROCESSING_METRICS_SUFFIX;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.ROUTE_ID_TAG;

public class MicroProfileMetricsRoutePolicy extends RoutePolicySupport implements NonManagedService {

    private MetricRegistry metricRegistry;
    private MetricsStatistics statistics;
    private MicroProfileMetricsRoutePolicyNamingStrategy namingStrategy = MicroProfileMetricsRoutePolicyNamingStrategy.DEFAULT;
    private MicroProfileMetricsExchangeRecorder exchangeRecorder;

    private static final class MetricsStatistics {
        private final MetricRegistry metricRegistry;
        private final Route route;
        private final MicroProfileMetricsRoutePolicyNamingStrategy namingStrategy;

        private MetricsStatistics(MetricRegistry metricRegistry, Route route, MicroProfileMetricsRoutePolicyNamingStrategy namingStrategy) {
            this.metricRegistry = ObjectHelper.notNull(metricRegistry, "metricRegistry", this);
            this.namingStrategy = ObjectHelper.notNull(namingStrategy, "MicroProfileMetricsRoutePolicyNamingStrategy", this);
            this.route = route;
        }

        public void onExchangeBegin(Exchange exchange) {
            String name = namingStrategy.getName(route);
            Timer timer = metricRegistry.timer(name + PROCESSING_METRICS_SUFFIX, namingStrategy.getTags(route));
            exchange.setProperty(propertyName(exchange), timer.time());
        }

        public void onExchangeDone(Exchange exchange) {
            Context context = (Context) exchange.removeProperty(propertyName(exchange));
            if (context != null) {
                context.stop();
            }
        }

        private String propertyName(Exchange exchange) {
            return String.format("%s.%s.%s", DEFAULT_CAMEL_ROUTE_POLICY_METRIC_NAME, route.getId(), exchange.getExchangeId());
        }
    }

    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    public void setMetricRegistry(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    public MicroProfileMetricsRoutePolicyNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(MicroProfileMetricsRoutePolicyNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    @Override
    public void onInit(Route route) {
        super.onInit(route);
        MetricRegistry metricRegistry = getMetricRegistry();
        if (metricRegistry == null) {
            metricRegistry = MicroProfileMetricsHelper.getMetricRegistry(route.getCamelContext());
        }

        exchangeRecorder = new MicroProfileMetricsExchangeRecorder(metricRegistry, namingStrategy.getName(route), namingStrategy.getTags(route));

        try {
            MicroProfileMetricsRoutePolicyService registryService = route.getCamelContext().hasService(MicroProfileMetricsRoutePolicyService.class);
            if (registryService == null) {
                registryService = new MicroProfileMetricsRoutePolicyService();
                registryService.setMetricRegistry(metricRegistry);
                route.getCamelContext().addService(registryService);
                ServiceHelper.startService(registryService);
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
        statistics = new MetricsStatistics(metricRegistry, route, getNamingStrategy());
    }

    @Override
    public void onExchangeBegin(Route route, Exchange exchange) {
        if (statistics != null) {
            statistics.onExchangeBegin(exchange);
        }

        if (exchangeRecorder != null) {
            exchangeRecorder.recordExchangeBegin();
        }
    }

    @Override
    public void onExchangeDone(Route route, Exchange exchange) {
        if (statistics != null) {
            statistics.onExchangeDone(exchange);
        }

        if (exchangeRecorder != null) {
            exchangeRecorder.recordExchangeComplete(exchange);
        }
    }

    @Override
    public void onRemove(Route route) {
        super.onRemove(route);

        MicroProfileMetricsHelper.removeMetricsFromRegistry(metricRegistry, (metricID, metric) -> {
            Map<String, String> tags = metricID.getTags();
            if (tags.containsKey(CAMEL_CONTEXT_TAG) && tags.containsKey(ROUTE_ID_TAG)) {
                String camelContextName = tags.get(CAMEL_CONTEXT_TAG);
                String routeId = tags.get(ROUTE_ID_TAG);
                return camelContextName.equals(route.getCamelContext().getName()) && routeId.equals(route.getId());
            }
            return false;
        });
    }
}
