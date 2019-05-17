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

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.Exchange;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.micrometer.MicrometerUtils;
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
    private MetricsStatistics statistics;
    private MicrometerRoutePolicyNamingStrategy namingStrategy = MicrometerRoutePolicyNamingStrategy.DEFAULT;

    private static final class MetricsStatistics {
        private final MeterRegistry meterRegistry;
        private final Route route;
        private final MicrometerRoutePolicyNamingStrategy namingStrategy;

        private MetricsStatistics(MeterRegistry meterRegistry, Route route, MicrometerRoutePolicyNamingStrategy namingStrategy) {
            this.meterRegistry = ObjectHelper.notNull(meterRegistry, "MeterRegistry", this);
            this.namingStrategy = ObjectHelper.notNull(namingStrategy, "MicrometerRoutePolicyNamingStrategy", this);
            this.route = route;
        }

        public void onExchangeBegin(Exchange exchange) {
            Timer.Sample sample = Timer.start(meterRegistry);
            exchange.setProperty(propertyName(exchange), sample);
        }

        public void onExchangeDone(Exchange exchange) {
            Timer.Sample sample = (Timer.Sample) exchange.removeProperty(propertyName(exchange));
            if (sample != null) {
                Timer timer = Timer.builder(namingStrategy.getName(route))
                        .tags(namingStrategy.getTags(route, exchange))
                        .description(route.getDescription())
                        .register(meterRegistry);
                sample.stop(timer);
            }
        }

        private String propertyName(Exchange exchange) {
            return String.format("%s-%s-%s", DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME, route.getId(), exchange.getExchangeId());
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

    @Override
    public void onInit(Route route) {
        super.onInit(route);
        if (getMeterRegistry() == null) {
            setMeterRegistry(MicrometerUtils.getOrCreateMeterRegistry(
                    route.getCamelContext().getRegistry(), METRICS_REGISTRY_NAME));
        }
        try {
            MicrometerRoutePolicyService registryService = route.getCamelContext().hasService(MicrometerRoutePolicyService.class);
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

        // create statistics holder
        // for now we record only all the timings of a complete exchange (responses)
        // we have in-flight / total statistics already from camel-core
        statistics = new MetricsStatistics(getMeterRegistry(), route, getNamingStrategy());
    }


    @Override
    public void onExchangeBegin(Route route, Exchange exchange) {
        if (statistics != null) {
            statistics.onExchangeBegin(exchange);
        }
    }

    @Override
    public void onExchangeDone(Route route, Exchange exchange) {
        if (statistics != null) {
            statistics.onExchangeDone(exchange);
        }
    }

}
