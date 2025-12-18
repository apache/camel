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
package org.apache.camel.opentelemetry.metrics.eventnotifier;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.opentelemetry.metrics.TaskTimer;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCreatedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeEvent;
import org.apache.camel.spi.CamelEvent.ExchangeFailedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.SimpleEventNotifierSupport;

public class OpenTelemetryExchangeEventNotifier extends EventNotifierSupport {

    private static final AtomicLong lastExchangeTimestampHolder = new AtomicLong(0);
    private final Class<ExchangeEvent> eventType = ExchangeEvent.class;
    private Meter meter;
    boolean registerTemplates = true;
    boolean registerKamelets;
    private InflightRepository inflightRepository;

    // Event Notifier options
    private Predicate<Exchange> ignoreExchanges = exchange -> false;
    private OpenTelemetryExchangeEventNotifierNamingStrategy namingStrategy
            = OpenTelemetryExchangeEventNotifierNamingStrategy.DEFAULT;
    boolean baseEndpointURI = true;
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    private TimeUnit lastExchangeTimeUnit = TimeUnit.MILLISECONDS;

    // Opentelemetry instruments
    private final Map<String, ObservableLongGauge> inflightGauges = new HashMap<>();
    private ObservableLongGauge lastExchangeTimeGauge;
    private LongHistogram elapsedTimer;
    private LongHistogram sentTimer;

    public OpenTelemetryExchangeEventNotifier() {
        // no-op
    }

    // Use the base endpoint to avoid increasing the number of separate events on dynamic endpoints (ie, toD).
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

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setLastExchangeTimeUnit(TimeUnit lastExchangeUnit) {
        this.lastExchangeTimeUnit = lastExchangeUnit;
    }

    public TimeUnit getLastExchangeTimeUnit() {
        return lastExchangeTimeUnit;
    }

    public void setNamingStrategy(OpenTelemetryExchangeEventNotifierNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    public OpenTelemetryExchangeEventNotifierNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setMeter(Meter meter) {
        this.meter = meter;
    }

    public Meter getMeter() {
        return meter;
    }

    @Override
    public boolean isEnabled(CamelEvent eventObject) {
        return eventType.isAssignableFrom(eventObject.getClass());
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        if (meter == null) {
            this.meter = CamelContextHelper.findSingleByType(getCamelContext(), Meter.class);
        }
        if (meter == null) {
            this.meter = GlobalOpenTelemetry.get().getMeter("camel");
        }
        if (meter == null) {
            throw new RuntimeCamelException("Could not find any OpenTelemetry meter!");
        }
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
            public void notify(CamelEvent event) {
                if (event instanceof CamelEvent.RouteAddedEvent addedEvent) {
                    addInFlightMessageGauge(addedEvent.getRoute());
                } else if (event instanceof CamelEvent.RouteRemovedEvent removedEvent) {
                    removeInFlightGauge(removedEvent.getRoute().getRouteId());
                }
            }
        });

        this.elapsedTimer = meter
                .histogramBuilder(getNamingStrategy().getElapsedTimerName())
                .setDescription("Time taken to complete exchange")
                .setUnit(timeUnit.name().toLowerCase())
                .ofLongs().build();

        this.sentTimer = meter
                .histogramBuilder(getNamingStrategy().getSentTimerName())
                .setDescription("Time taken to send message to the endpoint")
                .setUnit(timeUnit.name().toLowerCase())
                .ofLongs().build();

        this.lastExchangeTimeGauge = meter
                .gaugeBuilder(getNamingStrategy().getLastProcessedTimeName())
                .setDescription("Last exchange processed time since the Unix epoch")
                .ofLongs()
                .setUnit(lastExchangeTimeUnit.name().toLowerCase())
                .buildWithCallback(
                        observableMeasurement -> {
                            observableMeasurement.record(
                                    lastExchangeTimeUnit.convert(lastExchangeTimestampHolder.get(), TimeUnit.MILLISECONDS));
                        });

        // add existing routes
        for (Route route : getCamelContext().getRoutes()) {
            addInFlightMessageGauge(route);
        }
    }

    private void addInFlightMessageGauge(Route route) {
        boolean skip = (route.isCreatedByKamelet() && !registerKamelets)
                || (route.isCreatedByRouteTemplate() && !registerTemplates);
        if (!skip) {
            String routeId = route.getRouteId();
            String name = getNamingStrategy().getInflightExchangesName();
            Attributes attributes = getNamingStrategy().getInflightExchangesAttributes(getCamelContext(), routeId);
            ObservableLongGauge asyncGauge = meter.gaugeBuilder(name)
                    .setDescription("Route in flight messages")
                    .ofLongs()
                    .buildWithCallback(
                            observableMeasurement -> {
                                observableMeasurement.record(inflightRepository.size(routeId), attributes);
                            });
            inflightGauges.put(routeId, asyncGauge);
        }
    }

    private void removeInFlightGauge(String routeId) {
        ObservableLongGauge gauge = inflightGauges.remove(routeId);
        if (gauge != null) {
            gauge.close();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        inflightGauges.values().forEach(ObservableLongGauge::close);
        inflightGauges.clear();
        if (lastExchangeTimeGauge != null) {
            lastExchangeTimeGauge.close();
        }
    }

    @Override
    public void notify(CamelEvent eventObject) {
        if (eventObject instanceof ExchangeEvent exchangeEvent) {
            // skip routes that should not be included
            boolean skip = false;
            String routeId;
            Exchange exchange = exchangeEvent.getExchange();
            if (eventObject instanceof ExchangeCreatedEvent) {
                routeId = exchange.getFromRouteId();
            } else {
                routeId = ExchangeHelper.getAtRouteId(exchange);
            }
            if (routeId != null) {
                Route route = exchange.getContext().getRoute(routeId);
                if (route != null) {
                    skip = (route.isCreatedByKamelet() && !registerKamelets)
                            || (route.isCreatedByRouteTemplate() && !registerTemplates);
                }
            }
            if (skip) {
                return;
            }
            if (!(getIgnoreExchanges().test(exchange))) {
                if (eventObject instanceof ExchangeCreatedEvent createdEvent) {
                    handleCreatedEvent(createdEvent);
                } else if (eventObject instanceof ExchangeSentEvent sentEvent) {
                    handleSentEvent(sentEvent);
                } else if (eventObject instanceof ExchangeCompletedEvent
                        || eventObject instanceof ExchangeFailedEvent) {
                    handleDoneEvent(exchangeEvent);
                }
            }
        }
    }

    protected void handleSentEvent(ExchangeSentEvent sentEvent) {
        Attributes attributes = getNamingStrategy().getAttributes(sentEvent, sentEvent.getEndpoint(), isBaseEndpointURI());
        this.sentTimer.record(timeUnit.convert(sentEvent.getTimeTaken(), TimeUnit.MILLISECONDS), attributes);
    }

    protected void handleCreatedEvent(ExchangeCreatedEvent createdEvent) {
        String name = getNamingStrategy().getElapsedTimerName();
        createdEvent.getExchange().setProperty("elapsedTimer:" + name, new TaskTimer());
    }

    protected void handleDoneEvent(ExchangeEvent doneEvent) {
        String name = getNamingStrategy().getElapsedTimerName();
        TaskTimer task = (TaskTimer) doneEvent.getExchange().removeProperty("elapsedTimer:" + name);
        if (task != null) {
            Attributes attributes = getNamingStrategy().getAttributes(
                    doneEvent, doneEvent.getExchange().getFromEndpoint(), isBaseEndpointURI());
            this.elapsedTimer.record(task.duration(timeUnit), attributes);
        }
        lastExchangeTimestampHolder.set(System.currentTimeMillis());
    }
}
