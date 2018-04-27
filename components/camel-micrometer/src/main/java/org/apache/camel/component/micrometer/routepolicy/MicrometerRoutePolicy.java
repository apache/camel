/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.micrometer.routepolicy;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

/**
 * A {@link org.apache.camel.spi.RoutePolicy} which gathers statistics and reports them using {@link MeterRegistry}.
 * <p/>
 * The metrics is reported in JMX by default, but this can be configured.
 */
public class MicrometerRoutePolicy extends RoutePolicySupport implements NonManagedService {

    private MeterRegistry meterRegistry;
    private MicrometerRegistryService registryService;
    private boolean prettyPrint;
    private TimeUnit durationUnit = TimeUnit.MILLISECONDS;
    private MetricsStatistics statistics;
    private Route route;
    private String prefix = MicrometerConstants.HEADER_PREFIX;
    private String namePattern = "##name##.##routeId##.##type##";


    private static final class MetricsStatistics {
        private final MeterRegistry meterRegistry;
        private final Route route;
        private final String name;

        private MetricsStatistics(MeterRegistry meterRegistry, Route route, String name) {
            this.meterRegistry = meterRegistry;
            this.route = route;
            this.name = name;
        }

        public void onExchangeBegin(Exchange exchange) {
            Timer.Sample sample = Timer.start(meterRegistry);
            exchange.setProperty("MicrometerRoutePolicy-" + route.getId(), sample);
        }

        public void onExchangeDone(Exchange exchange) {
            Timer.Sample sample = (Timer.Sample) exchange.removeProperty("MicrometerRoutePolicy-" + route.getId());
            if (sample != null) {
                Timer timer = Timer.builder(name)
                        .description(route.getDescription())
                        .tag("camelService", "routePolicy")
                        .tag("route", route.getId())
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

    public String getNamePattern() {
        return namePattern;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    /**
     * The name pattern to use.
     * <p/>
     * Uses dot as separators, but you can change that.
     * The values <tt>##name##</tt>, <tt>##routeId##</tt>, and <tt>##type##</tt> will be replaced with actual value.
     */
    public void setNamePattern(String namePattern) {
        this.namePattern = namePattern;
    }

    @Override
    public void onInit(Route route) {
        super.onInit(route);

        this.route = route;
        try {
            registryService = route.getRouteContext().getCamelContext().hasService(MicrometerRegistryService.class);
            if (registryService == null) {
                registryService = new MicrometerRegistryService();
                registryService.setMeterRegistry(getMeterRegistry());
                registryService.setPrettyPrint(isPrettyPrint());
                registryService.setDurationUnit(getDurationUnit());
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
        statistics = new MetricsStatistics(meterRegistry, route, createName("responses"));
    }

    private String createName(String type) {
        CamelContext context = route.getRouteContext().getCamelContext();
        String name = context.getManagementName() != null ? context.getManagementName() : context.getName();

        String answer = namePattern;
        answer = answer.replaceFirst("##prefix##", prefix);
        answer = answer.replaceFirst("##name##", name);
        answer = answer.replaceFirst("##routeId##", Matcher.quoteReplacement(route.getId()));
        answer = answer.replaceFirst("##type##", type);
        return answer;
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
