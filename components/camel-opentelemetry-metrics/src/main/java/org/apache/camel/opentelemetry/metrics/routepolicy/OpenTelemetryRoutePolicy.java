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
package org.apache.camel.opentelemetry.metrics.routepolicy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.opentelemetry.metrics.TaskTimer;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.CAMEL_CONTEXT_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.EVENT_TYPE_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.KIND_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.KIND_ROUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.ROUTE_ID_ATTRIBUTE;

/**
 * A {@link org.apache.camel.spi.RoutePolicy} to plugin and use OpenTelemetry metrics for gathering route utilization
 * statistics.
 */
public class OpenTelemetryRoutePolicy extends RoutePolicySupport implements NonManagedService {

    private static final Logger LOG = LoggerFactory.getLogger(OpenTelemetryRoutePolicy.class);

    private final OpenTelemetryRoutePolicyFactory factory;
    private Meter meter;
    boolean registerKamelets;
    boolean registerTemplates = true;

    private OpenTelemetryRoutePolicyNamingStrategy namingStrategy = OpenTelemetryRoutePolicyNamingStrategy.DEFAULT;
    private OpenTelemetryRoutePolicyConfiguration configuration = new OpenTelemetryRoutePolicyConfiguration();
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    private final Map<Route, MetricsStatistics> statisticsMap = new HashMap<>();

    public OpenTelemetryRoutePolicy(OpenTelemetryRoutePolicyFactory factory) {
        this.factory = factory;
    }

    public Meter getMeter() {
        return meter;
    }

    public void setMeter(Meter meter) {
        this.meter = meter;
    }

    public OpenTelemetryRoutePolicyNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(OpenTelemetryRoutePolicyNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    public OpenTelemetryRoutePolicyConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(OpenTelemetryRoutePolicyConfiguration configuration) {
        this.configuration = configuration;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    boolean isRegisterKamelets() {
        return registerKamelets;
    }

    boolean isRegisterTemplates() {
        return registerTemplates;
    }

    @Override
    public void onInit(Route route) {
        super.onInit(route);
        if (meter == null) {
            this.meter = CamelContextHelper.findSingleByType(route.getCamelContext(), Meter.class);
        }
        if (meter == null) {
            this.meter = GlobalOpenTelemetry.get().getMeter("camel");
        }
        if (meter == null) {
            throw new RuntimeCamelException("Could not find any OpenTelemetry meter!");
        }

        ManagementStrategy ms = route.getCamelContext().getManagementStrategy();
        if (ms != null && ms.getManagementAgent() != null) {
            registerKamelets = ms.getManagementAgent().getRegisterRoutesCreateByKamelet();
            registerTemplates = ms.getManagementAgent().getRegisterRoutesCreateByTemplate();
        }
    }

    @Override
    public void onStart(Route route) {
        // create statistics holder
        statisticsMap.computeIfAbsent(route,
                it -> {
                    boolean skip = !configuration.isRouteEnabled();
                    // skip routes that should not be included
                    if (!skip) {
                        skip = (it.isCreatedByKamelet() && !registerKamelets)
                                || (it.isCreatedByRouteTemplate() && !registerTemplates);
                    }
                    if (!skip && configuration.getExcludePattern() != null) {
                        String[] patterns = configuration.getExcludePattern().split(",");
                        skip = PatternHelper.matchPatterns(route.getRouteId(), patterns);
                    }
                    LOG.debug("Capturing metrics for route: {} -> {}", route.getRouteId(), skip);
                    if (skip) {
                        return null;
                    }
                    return new MetricsStatistics(
                            meter, it.getCamelContext(), it, getNamingStrategy(), configuration, timeUnit);
                });
    }

    @Override
    public void onRemove(Route route) {
        statisticsMap.remove(route);
    }

    @Override
    public void onExchangeBegin(Route route, Exchange exchange) {
        Optional.ofNullable(statisticsMap.get(route))
                .ifPresent(statistics -> statistics.onExchangeBegin(exchange));
    }

    @Override
    public void onExchangeDone(Route route, Exchange exchange) {
        Optional.ofNullable(statisticsMap.get(route))
                .ifPresent(statistics -> statistics.onExchangeDone(exchange));
    }

    static class MetricsStatistics implements RouteMetric {
        private final CamelContext camelContext;
        private final Route route;

        // Configuration
        private final OpenTelemetryRoutePolicyNamingStrategy namingStrategy;
        private final OpenTelemetryRoutePolicyConfiguration configuration;
        private final TimeUnit timeUnit;

        // OpenTelemetry objects
        private final Meter meter;
        private final Attributes attributes;

        // OpenTelemetry instruments
        private final LongHistogram timer;
        private LongCounter exchangesSucceeded;
        private LongCounter exchangesFailed;
        private LongCounter exchangesTotal;
        private LongCounter externalRedeliveries;
        private LongCounter failuresHandled;

        MetricsStatistics(Meter meter, CamelContext camelContext, Route route,
                          OpenTelemetryRoutePolicyNamingStrategy namingStrategy,
                          OpenTelemetryRoutePolicyConfiguration configuration,
                          TimeUnit timeUnit) {

            this.configuration = ObjectHelper.notNull(configuration, "OpenTelemetryRoutePolicyConfiguration", this);
            this.namingStrategy = ObjectHelper.notNull(namingStrategy, "OpenTelemetryRoutePolicyNamingStrategy", this);
            this.meter = ObjectHelper.notNull(meter, "Meter", this);
            this.camelContext = camelContext;
            this.route = route;
            this.timeUnit = timeUnit;
            this.attributes = Attributes.of(
                    AttributeKey.stringKey(CAMEL_CONTEXT_ATTRIBUTE),
                    route != null ? route.getCamelContext().getName() : camelContext.getName(),
                    AttributeKey.stringKey(KIND_ATTRIBUTE), KIND_ROUTE,
                    AttributeKey.stringKey(ROUTE_ID_ATTRIBUTE), route != null ? route.getId() : "",
                    AttributeKey.stringKey(EVENT_TYPE_ATTRIBUTE), route != null ? "route" : "context");
            this.timer = meter
                    .histogramBuilder(namingStrategy.getName(route))
                    .setDescription(route != null ? "Route performance metrics" : "CamelContext performance metrics")
                    .setUnit(timeUnit.name().toLowerCase())
                    .ofLongs().build();

            if (configuration.isAdditionalCounters()) {
                initAdditionalCounters();
            }
        }

        private void initAdditionalCounters() {
            if (configuration.isExchangesSucceeded()) {
                this.exchangesSucceeded = createCounter(namingStrategy.getExchangesSucceededName(route),
                        "Number of successfully completed exchanges");
            }
            if (configuration.isExchangesFailed()) {
                this.exchangesFailed
                        = createCounter(namingStrategy.getExchangesFailedName(route),
                                "Number of failed exchanges");
            }
            if (configuration.isExchangesTotal()) {
                this.exchangesTotal
                        = createCounter(namingStrategy.getExchangesTotalName(route),
                                "Total number of processed exchanges");
            }
            if (configuration.isExternalRedeliveries()) {
                this.externalRedeliveries = createCounter(namingStrategy.getExternalRedeliveriesName(route),
                        "Number of external initiated redeliveries (such as from JMS broker)");
            }
            if (configuration.isFailuresHandled()) {
                this.failuresHandled
                        = createCounter(namingStrategy.getFailuresHandledName(route),
                                "Number of failures handled");
            }
        }

        @Override
        public void onExchangeBegin(Exchange exchange) {
            String propertyName = propertyName(exchange);
            exchange.setProperty(propertyName, new TaskTimer());
        }

        @Override
        public void onExchangeDone(Exchange exchange) {
            String propertyName = propertyName(exchange);
            TaskTimer task = (TaskTimer) exchange.removeProperty(propertyName);
            if (task != null) {
                this.timer.record(task.duration(timeUnit), attributes);
            }
            TaskTimer longTask = (TaskTimer) exchange.removeProperty(propertyName + "_long_task");
            if (longTask != null) {
                longTask.stop();
            }
            if (configuration.isAdditionalCounters()) {
                updateAdditionalCounters(exchange);
            }
        }

        @Override
        public void remove() {
            // no-op
        }

        private void updateAdditionalCounters(Exchange exchange) {
            if (exchangesTotal != null) {
                exchangesTotal.add(1L, attributes);
            }
            if (exchange.isFailed()) {
                if (exchangesFailed != null) {
                    exchangesFailed.add(1L, attributes);
                }
            } else {
                if (exchangesSucceeded != null) {
                    exchangesSucceeded.add(1L, attributes);
                }
                if (failuresHandled != null && ExchangeHelper.isFailureHandled(exchange)) {
                    failuresHandled.add(1L, attributes);
                }
                if (externalRedeliveries != null && exchange.isExternalRedelivered()) {
                    externalRedeliveries.add(1L, attributes);
                }
            }
        }

        private String propertyName(Exchange exchange) {
            String id = route != null ? route.getId() : "context:" + camelContext.getName();
            return String.format("%s-%s-%s", DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME, id, exchange.getExchangeId());
        }

        private LongCounter createCounter(String meterName, String description) {
            return meter.counterBuilder(meterName)
                    .setDescription(description).build();
        }
    }
}
