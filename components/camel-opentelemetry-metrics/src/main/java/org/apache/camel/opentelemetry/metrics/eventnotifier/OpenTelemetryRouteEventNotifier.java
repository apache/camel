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

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.RouteEvent;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.EventNotifierSupport;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.CAMEL_CONTEXT_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.EVENT_TYPE_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.KIND_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.KIND_ROUTE;

public class OpenTelemetryRouteEventNotifier extends EventNotifierSupport {

    private final Class<RouteEvent> eventType = RouteEvent.class;
    private Meter meter;
    boolean registerKamelets;
    boolean registerTemplates = true;
    private Attributes attributes = Attributes.of(
            AttributeKey.stringKey(KIND_ATTRIBUTE), KIND_ROUTE,
            AttributeKey.stringKey(EVENT_TYPE_ATTRIBUTE), RouteEvent.class.getSimpleName());

    // Event Notifier options
    private OpenTelemetryRouteEventNotifierNamingStrategy namingStrategy
            = OpenTelemetryRouteEventNotifierNamingStrategy.DEFAULT;

    // OpenTelemetry instruments
    private LongUpDownCounter addedCounter;
    private LongUpDownCounter runningCounter;
    private LongCounter reloadedCounter;

    public OpenTelemetryRouteEventNotifier() {
        // no-op
    }

    public void setNamingStrategy(OpenTelemetryRouteEventNotifierNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    public OpenTelemetryRouteEventNotifierNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setMeter(Meter meter) {
        this.meter = meter;
    }

    public Meter getMeter() {
        return meter;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        super.setCamelContext(camelContext);
        this.attributes = Attributes.of(
                AttributeKey.stringKey(KIND_ATTRIBUTE), KIND_ROUTE,
                AttributeKey.stringKey(CAMEL_CONTEXT_ATTRIBUTE), camelContext.getName(),
                AttributeKey.stringKey(EVENT_TYPE_ATTRIBUTE), RouteEvent.class.getSimpleName());
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
        addedCounter = meter.upDownCounterBuilder(namingStrategy.getRouteAddedName())
                .setUnit("routes").build();
        runningCounter = meter.upDownCounterBuilder(namingStrategy.getRouteRunningName())
                .setUnit("routes").build();
        reloadedCounter = meter.counterBuilder(namingStrategy.getRouteReloadedName())
                .setUnit("routes").build();
    }

    @Override
    public void notify(CamelEvent event) {
        if (event instanceof RouteEvent re) {
            // skip routes that should not be included
            boolean skip = (re.getRoute().isCreatedByKamelet() && !registerKamelets)
                    || (re.getRoute().isCreatedByRouteTemplate() && !registerTemplates);
            if (skip) {
                return;
            }
        }
        if (event instanceof CamelEvent.RouteAddedEvent) {
            addedCounter.add(1L, attributes);
        } else if (event instanceof CamelEvent.RouteRemovedEvent) {
            addedCounter.add(-1L, attributes);
        } else if (event instanceof CamelEvent.RouteStartedEvent) {
            runningCounter.add(1L, attributes);
        } else if (event instanceof CamelEvent.RouteStoppedEvent) {
            runningCounter.add(-1L, attributes);
        } else if (event instanceof CamelEvent.RouteReloadedEvent) {
            reloadedCounter.add(1L, attributes);
        }
    }
}
