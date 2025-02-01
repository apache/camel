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
package org.apache.camel.component.micrometer.eventnotifier;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCreatedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeEvent;
import org.apache.camel.spi.CamelEvent.ExchangeFailedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.SimpleEventNotifierSupport;

public class MicrometerExchangeEventNotifier extends AbstractMicrometerEventNotifier<ExchangeEvent> {
    private InflightRepository inflightRepository;

    private final Map<String, Meter> meterMap = new HashMap<>();
    private Predicate<Exchange> ignoreExchanges = exchange -> false;
    private MicrometerExchangeEventNotifierNamingStrategy namingStrategy;
    boolean registerKamelets;
    boolean registerTemplates = true;
    boolean baseEndpointURI = true;

    public MicrometerExchangeEventNotifier() {
        super(ExchangeEvent.class);
    }

    public void setBaseEndpointURI(boolean baseEndpointURI) {
        this.baseEndpointURI = baseEndpointURI;
    }

    public boolean isBaseEndpointURI() {
        return baseEndpointURI;
    }

    public void setIgnoreExchanges(Predicate<Exchange> ignoreExchanges) {
        this.ignoreExchanges = ignoreExchanges;
    }

    public Predicate<Exchange> getIgnoreExchanges() {
        return ignoreExchanges;
    }

    public MicrometerExchangeEventNotifierNamingStrategy getNamingStrategy() {
        if (namingStrategy == null) {
            // Fallback to default if none is provided
            this.namingStrategy = new MicrometerExchangeEventNotifierNamingStrategyDefault(isBaseEndpointURI());
        }
        return namingStrategy;
    }

    public void setNamingStrategy(MicrometerExchangeEventNotifierNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    @Override
    protected void doInit() throws Exception {
        ManagementStrategy ms = getCamelContext().getManagementStrategy();
        if (ms != null && ms.getManagementAgent() != null) {
            registerKamelets = ms.getManagementAgent().getRegisterRoutesCreateByKamelet();
            registerTemplates = ms.getManagementAgent().getRegisterRoutesCreateByTemplate();
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        inflightRepository = getCamelContext().getInflightRepository();

        // need to be able to add/remove meter accordingly to route changes
        getCamelContext().getManagementStrategy().addEventNotifier(new SimpleEventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) throws Exception {
                if (event instanceof CamelEvent.RouteAddedEvent rre) {
                    addInflightMeter(rre.getRoute());
                } else if (event instanceof CamelEvent.RouteRemovedEvent rre) {
                    removeInflightMeter(rre.getRoute().getRouteId());
                }
            }
        });

        // add existing routes
        for (Route route : getCamelContext().getRoutes()) {
            addInflightMeter(route);
        }
    }

    private void addInflightMeter(Route route) {
        boolean skip = (route.isCreatedByKamelet() && !registerKamelets)
                || (route.isCreatedByRouteTemplate() && !registerTemplates);
        if (!skip) {
            String routeId = route.getRouteId();
            String name = getNamingStrategy().getInflightExchangesName();
            Tags tags = getNamingStrategy().getInflightExchangesTags(getCamelContext(), routeId);
            Meter meter = Gauge.builder(name, () -> inflightRepository.size(routeId))
                    .description("Route inflight messages")
                    .tags(tags)
                    .register(getMeterRegistry());
            meterMap.put(routeId, meter);
        }
    }

    private void removeInflightMeter(String routeId) {
        Meter meter = meterMap.remove(routeId);
        if (meter != null) {
            getMeterRegistry().remove(meter);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        // remove all meters
        meterMap.values().forEach(m -> getMeterRegistry().remove(m));
        meterMap.clear();
    }

    @Override
    public void notify(CamelEvent eventObject) {
        if (eventObject instanceof ExchangeEvent ee) {
            // skip routes that should not be included
            boolean skip = false;
            String routeId;
            if (eventObject instanceof ExchangeCreatedEvent) {
                routeId = ee.getExchange().getFromRouteId();
            } else {
                routeId = ExchangeHelper.getAtRouteId(ee.getExchange());
            }
            if (routeId != null) {
                Route route = ee.getExchange().getContext().getRoute(routeId);
                if (route != null) {
                    skip = (route.isCreatedByKamelet() && !registerKamelets)
                            || (route.isCreatedByRouteTemplate() && !registerTemplates);
                }
            }
            if (skip) {
                return;
            }
            if (!(getIgnoreExchanges().test(ee.getExchange()))) {
                if (eventObject instanceof ExchangeCreatedEvent) {
                    handleCreatedEvent((ExchangeCreatedEvent) eventObject);
                } else if (eventObject instanceof ExchangeSentEvent) {
                    handleSentEvent((ExchangeSentEvent) eventObject);
                } else if (eventObject instanceof ExchangeCompletedEvent || eventObject instanceof ExchangeFailedEvent) {
                    handleDoneEvent((ExchangeEvent) eventObject);
                }
            }
        }
    }

    protected void handleSentEvent(ExchangeSentEvent sentEvent) {
        String name = getNamingStrategy().getName(sentEvent.getExchange(), sentEvent.getEndpoint());
        Tags tags = getNamingStrategy().getTags(sentEvent, sentEvent.getEndpoint());
        Timer timer = Timer.builder(name).tags(tags).description("Time taken to send message to the endpoint")
                .register(getMeterRegistry());
        timer.record(sentEvent.getTimeTaken(), TimeUnit.MILLISECONDS);
    }

    protected void handleCreatedEvent(ExchangeCreatedEvent createdEvent) {
        String name = getNamingStrategy().getName(createdEvent.getExchange(), createdEvent.getExchange().getFromEndpoint());
        createdEvent.getExchange().setProperty("eventTimer:" + name, Timer.start(getMeterRegistry()));
    }

    protected void handleDoneEvent(ExchangeEvent doneEvent) {
        String name = getNamingStrategy().getName(doneEvent.getExchange(), doneEvent.getExchange().getFromEndpoint());
        Tags tags = getNamingStrategy().getTags(doneEvent, doneEvent.getExchange().getFromEndpoint());
        // Would have preferred LongTaskTimer, but you cannot set the FAILED_TAG once it is registered
        Timer.Sample sample = (Timer.Sample) doneEvent.getExchange().removeProperty("eventTimer:" + name);
        if (sample != null) {
            sample.stop(getMeterRegistry().timer(name, tags));
        }
    }

}
