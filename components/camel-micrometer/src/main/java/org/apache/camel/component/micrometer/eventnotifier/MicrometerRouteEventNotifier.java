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

import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Gauge;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.RouteAddedEvent;
import org.apache.camel.spi.CamelEvent.RouteEvent;
import org.apache.camel.spi.CamelEvent.RouteReloadedEvent;
import org.apache.camel.spi.CamelEvent.RouteRemovedEvent;
import org.apache.camel.spi.CamelEvent.RouteStartedEvent;
import org.apache.camel.spi.CamelEvent.RouteStoppedEvent;
import org.apache.camel.spi.ManagementStrategy;

public class MicrometerRouteEventNotifier extends AbstractMicrometerEventNotifier<RouteEvent> {

    private final AtomicLong routesAdded = new AtomicLong();
    private final AtomicLong routesRunning = new AtomicLong();
    private final AtomicLong routesReloaded = new AtomicLong();
    private Gauge gaugeAdded;
    private Gauge gaugeRunning;
    private Gauge gaugeReloaded;
    private MicrometerRouteEventNotifierNamingStrategy namingStrategy = MicrometerRouteEventNotifierNamingStrategy.DEFAULT;
    boolean registerKamelets;
    boolean registerTemplates = true;

    public MicrometerRouteEventNotifier() {
        super(RouteEvent.class);
    }

    public MicrometerRouteEventNotifierNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(MicrometerRouteEventNotifierNamingStrategy namingStrategy) {
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

        gaugeAdded = Gauge.builder(namingStrategy.getRouteAddedName(), routesAdded, value -> (double) value.get())
                .baseUnit("routes")
                .tags(namingStrategy.getTags(getCamelContext()))
                .register(getMeterRegistry());
        gaugeRunning = Gauge.builder(namingStrategy.getRouteRunningName(), routesRunning, value -> (double) value.get())
                .baseUnit("routes")
                .tags(namingStrategy.getTags(getCamelContext()))
                .register(getMeterRegistry());
        gaugeReloaded = Gauge.builder(namingStrategy.getRouteReloadedName(), routesReloaded, value -> (double) value.get())
                .baseUnit("routes")
                .tags(namingStrategy.getTags(getCamelContext()))
                .register(getMeterRegistry());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (gaugeAdded != null) {
            getMeterRegistry().remove(gaugeAdded);
        }
        if (gaugeRunning != null) {
            getMeterRegistry().remove(gaugeRunning);
        }
        if (gaugeReloaded != null) {
            getMeterRegistry().remove(gaugeReloaded);
        }
    }

    @Override
    public void notify(CamelEvent eventObject) {
        if (eventObject instanceof RouteEvent re) {
            // skip routes that should not be included
            boolean skip = (re.getRoute().isCreatedByKamelet() && !registerKamelets)
                    || (re.getRoute().isCreatedByRouteTemplate() && !registerTemplates);
            if (skip) {
                return;
            }
        }
        if (eventObject instanceof RouteAddedEvent) {
            routesAdded.incrementAndGet();
        } else if (eventObject instanceof RouteRemovedEvent) {
            routesAdded.decrementAndGet();
        } else if (eventObject instanceof RouteStartedEvent) {
            routesRunning.incrementAndGet();
        } else if (eventObject instanceof RouteStoppedEvent) {
            routesRunning.decrementAndGet();
        } else if (eventObject instanceof RouteReloadedEvent) {
            routesReloaded.incrementAndGet();
        }
    }
}
