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
package org.apache.camel.component.micrometer.routepolicy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.Exchange;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.micrometer.MicrometerUtils;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.METRICS_REGISTRY_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.SERVICE_NAME;

/**
 * A {@link org.apache.camel.spi.RoutePolicy} which gathers statistics and reports them using {@link MeterRegistry}.
 * <p/>
 * The metrics is reported in JMX by default, but this can be configured.
 */
public class MicrometerRoutePolicy extends RoutePolicySupport implements NonManagedService {

    private MeterRegistry meterRegistry;
    private boolean prettyPrint;
    private TimeUnit durationUnit = TimeUnit.MILLISECONDS;
    private MicrometerRoutePolicyNamingStrategy namingStrategy = MicrometerRoutePolicyNamingStrategy.DEFAULT;
    private MicrometerRoutePolicyConfiguration configuration = MicrometerRoutePolicyConfiguration.DEFAULT;

    private final Map<Route, MetricsStatistics> statisticsMap = new HashMap<>();
    boolean registerKamelets;
    boolean registerTemplates = true;

    private static final class MetricsStatistics {
        private final MeterRegistry meterRegistry;
        private final Route route;
        private final MicrometerRoutePolicyNamingStrategy namingStrategy;
        private final MicrometerRoutePolicyConfiguration configuration;
        private Counter exchangesSucceeded;
        private Counter exchangesFailed;
        private Counter exchangesTotal;
        private Counter externalRedeliveries;
        private Counter failuresHandled;
        private Timer timer;
        private LongTaskTimer longTaskTimer;

        private MetricsStatistics(MeterRegistry meterRegistry, Route route,
                                  MicrometerRoutePolicyNamingStrategy namingStrategy,
                                  MicrometerRoutePolicyConfiguration configuration) {
            this.configuration = ObjectHelper.notNull(configuration, "MicrometerRoutePolicyConfiguration", this);
            this.meterRegistry = ObjectHelper.notNull(meterRegistry, "MeterRegistry", this);
            this.namingStrategy = ObjectHelper.notNull(namingStrategy, "MicrometerRoutePolicyNamingStrategy", this);
            this.route = route;
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
                        = createCounter(namingStrategy.getExchangesFailedName(route), "Number of failed exchanges");
            }
            if (configuration.isExchangesTotal()) {
                this.exchangesTotal
                        = createCounter(namingStrategy.getExchangesTotalName(route), "Total number of processed exchanges");
            }
            if (configuration.isExternalRedeliveries()) {
                this.externalRedeliveries = createCounter(namingStrategy.getExternalRedeliveriesName(route),
                        "Number of external initiated redeliveries (such as from JMS broker)");
            }
            if (configuration.isFailuresHandled()) {
                this.failuresHandled
                        = createCounter(namingStrategy.getFailuresHandledName(route), "Number of failures handled");
            }
            if (configuration.isLongTask()) {
                LongTaskTimer.Builder builder = LongTaskTimer.builder(namingStrategy.getLongTaskName(route))
                        .tags(namingStrategy.getTags(route))
                        .description("Route long task metric");
                if (configuration.getLongTaskInitiator() != null) {
                    configuration.getLongTaskInitiator().accept(builder);
                }
                longTaskTimer = builder.register(meterRegistry);
            }
        }

        public void onExchangeBegin(Exchange exchange) {
            Timer.Sample sample = Timer.start(meterRegistry);
            exchange.setProperty(propertyName(exchange), sample);
            if (longTaskTimer != null) {
                exchange.setProperty(propertyName(exchange) + "_long_task", longTaskTimer.start());
            }
        }

        public void onExchangeDone(Exchange exchange) {
            Timer.Sample sample = (Timer.Sample) exchange.removeProperty(propertyName(exchange));
            if (sample != null) {
                if (timer == null) {
                    Timer.Builder builder = Timer.builder(namingStrategy.getName(route))
                            .tags(namingStrategy.getTags(route))
                            .description("Route performance metrics");
                    if (configuration.getTimerInitiator() != null) {
                        configuration.getTimerInitiator().accept(builder);
                    }
                    timer = builder.register(meterRegistry);
                }
                sample.stop(timer);
            }
            LongTaskTimer.Sample ltSampler
                    = (LongTaskTimer.Sample) exchange.removeProperty(propertyName(exchange) + "_long_task");
            if (ltSampler != null) {
                ltSampler.stop();
            }
            if (configuration.isAdditionalCounters()) {
                updateAdditionalCounters(exchange);
            }
        }

        private void updateAdditionalCounters(Exchange exchange) {
            if (exchangesTotal != null) {
                exchangesTotal.increment();
            }
            if (exchange.isFailed()) {
                if (exchangesFailed != null) {
                    exchangesFailed.increment();
                }
            } else {
                if (exchangesSucceeded != null) {
                    exchangesSucceeded.increment();
                }
                if (failuresHandled != null && ExchangeHelper.isFailureHandled(exchange)) {
                    failuresHandled.increment();
                }
                if (externalRedeliveries != null && exchange.isExternalRedelivered()) {
                    externalRedeliveries.increment();
                }
            }
        }

        private String propertyName(Exchange exchange) {
            return String.format("%s-%s-%s", DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME, route.getId(), exchange.getExchangeId());
        }

        private Counter createCounter(String meterName, String description) {
            return Counter.builder(meterName)
                    .tags(namingStrategy.getExchangeStatusTags(route))
                    .description(description)
                    .register(meterRegistry);
        }
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    public void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public TimeUnit getDurationUnit() {
        return durationUnit;
    }

    public void setDurationUnit(TimeUnit durationUnit) {
        this.durationUnit = durationUnit;
    }

    public MicrometerRoutePolicyNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(MicrometerRoutePolicyNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    public MicrometerRoutePolicyConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(MicrometerRoutePolicyConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void onInit(Route route) {
        super.onInit(route);

        ManagementStrategy ms = route.getCamelContext().getManagementStrategy();
        if (ms != null && ms.getManagementAgent() != null) {
            registerKamelets = ms.getManagementAgent().getRegisterRoutesCreateByKamelet();
            registerTemplates = ms.getManagementAgent().getRegisterRoutesCreateByTemplate();
        }

        if (getMeterRegistry() == null) {
            setMeterRegistry(MicrometerUtils.getOrCreateMeterRegistry(
                    route.getCamelContext().getRegistry(), METRICS_REGISTRY_NAME));
        }
        try {
            MicrometerRoutePolicyService registryService
                    = route.getCamelContext().hasService(MicrometerRoutePolicyService.class);
            if (registryService == null) {
                registryService = new MicrometerRoutePolicyService();
                registryService.setMeterRegistry(getMeterRegistry());
                registryService.setPrettyPrint(isPrettyPrint());
                registryService.setDurationUnit(getDurationUnit());
                registryService.setMatchingTags(Tags.of(SERVICE_NAME, MicrometerRoutePolicyService.class.getSimpleName()));
                route.getCamelContext().addService(registryService);
                ServiceHelper.startService(registryService);
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    @Override
    public void onStart(Route route) {
        // create statistics holder
        // for now we record only all the timings of a complete exchange (responses)
        // we have in-flight / total statistics already from camel-core
        statisticsMap.computeIfAbsent(route,
                it -> {
                    // skip routes that should not be included
                    boolean skip = (it.isCreatedByKamelet() && !registerKamelets)
                            || (it.isCreatedByRouteTemplate() && !registerTemplates);
                    if (skip) {
                        return null;
                    }
                    return new MetricsStatistics(getMeterRegistry(), it, getNamingStrategy(), configuration);
                });
    }

    @Override
    public void onRemove(Route route) {
        // route is removed, so remove metrics from micrometer
        MetricsStatistics stats = statisticsMap.remove(route);
        if (stats != null) {
            if (stats.exchangesSucceeded != null) {
                meterRegistry.remove(stats.exchangesSucceeded);
            }
            if (stats.exchangesFailed != null) {
                meterRegistry.remove(stats.exchangesFailed);
            }
            if (stats.exchangesTotal != null) {
                meterRegistry.remove(stats.exchangesTotal);
            }
            if (stats.externalRedeliveries != null) {
                meterRegistry.remove(stats.externalRedeliveries);
            }
            if (stats.failuresHandled != null) {
                meterRegistry.remove(stats.failuresHandled);
            }
            if (stats.timer != null) {
                meterRegistry.remove(stats.timer);
            }
            if (stats.longTaskTimer != null) {
                meterRegistry.remove(stats.longTaskTimer);
            }
        }
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

}
