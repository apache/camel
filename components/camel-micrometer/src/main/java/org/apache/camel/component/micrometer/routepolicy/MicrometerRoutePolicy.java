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
package org.apache.camel.component.micrometer.routepolicy;

import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.Exchange;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import static org.apache.camel.component.micrometer.MicrometerConstants.CAMEL_CONTEXT_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.ROUTE_ID_TAG;

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


    private static final class MetricsStatistics {
        private static final String MICROMETER_ROUTE_POLICY = "MicrometerRoutePolicy-";
        private final MeterRegistry meterRegistry;
        private final Route route;

        private MetricsStatistics(MeterRegistry meterRegistry, Route route) {
            this.meterRegistry = meterRegistry;
            this.route = route;
        }

        public void onExchangeBegin(Exchange exchange) {
            Timer.Sample sample = Timer.start(meterRegistry);
            exchange.setProperty(MICROMETER_ROUTE_POLICY + route.getId(), sample);
        }

        public void onExchangeDone(Exchange exchange) {
            Timer.Sample sample = (Timer.Sample) exchange.removeProperty(MICROMETER_ROUTE_POLICY + route.getId());
            if (sample != null) {
                Timer timer = Timer.builder(DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME)
                        .description(route.getDescription())
                        .tag(CAMEL_CONTEXT_TAG, route.getRouteContext().getCamelContext().getName())
                        .tag(ROUTE_ID_TAG, route.getId())
                        .tag("failed", Boolean.toString(exchange.isFailed()))
                        .register(meterRegistry);
                sample.stop(timer);
            }
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

    @Override
    public void onInit(Route route) {
        super.onInit(route);

        MicrometerRoutePolicyService registryService;
        try {
            registryService = route.getRouteContext().getCamelContext().hasService(MicrometerRoutePolicyService.class);
            if (registryService == null) {
                registryService = new MicrometerRoutePolicyService();
                registryService.setMeterRegistry(getMeterRegistry());
                registryService.setPrettyPrint(isPrettyPrint());
                registryService.setDurationUnit(getDurationUnit());
                registryService.setMatchingNames(name -> name.equals(DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME));
                route.getRouteContext().getCamelContext().addService(registryService);
            }
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }

        // ensure registry service is started
        try {
            ServiceHelper.startService(registryService);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }

        // create statistics holder
        // for know we record only all the timings of a complete exchange (responses)
        // we have in-flight / total statistics already from camel-core
        statistics = new MetricsStatistics(meterRegistry, route);
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
