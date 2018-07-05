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
package org.apache.camel.component.micrometer.eventnotifier;

import java.util.EventObject;
import java.util.concurrent.atomic.AtomicLong;
import io.micrometer.core.instrument.Gauge;
import org.apache.camel.management.event.AbstractRouteEvent;
import org.apache.camel.management.event.RouteAddedEvent;
import org.apache.camel.management.event.RouteRemovedEvent;
import org.apache.camel.management.event.RouteStartedEvent;
import org.apache.camel.management.event.RouteStoppedEvent;


public class MicrometerRouteEventNotifier extends AbstractMicrometerEventNotifier<AbstractRouteEvent> {

    private final AtomicLong routesAdded = new AtomicLong();
    private final AtomicLong routesRunning = new AtomicLong();
    private MicrometerRouteEventNotifierNamingStrategy namingStrategy = MicrometerRouteEventNotifierNamingStrategy.DEFAULT;

    public MicrometerRouteEventNotifier() {
        super(AbstractRouteEvent.class);
    }

    public MicrometerRouteEventNotifierNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(MicrometerRouteEventNotifierNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        Gauge.builder(namingStrategy.getRouteAddedName(), routesAdded, value -> Long.valueOf(value.get()).doubleValue())
                .baseUnit("routes")
                .tags(namingStrategy.getTags(getCamelContext()))
                .register(getMeterRegistry());
        Gauge.builder(namingStrategy.getRouteRunningName(), routesRunning, value -> Long.valueOf(value.get()).doubleValue())
                .baseUnit("routes")
                .tags(namingStrategy.getTags(getCamelContext()))
                .register(getMeterRegistry());
    }

    @Override
    public void notify(EventObject eventObject) {
        if (eventObject instanceof RouteAddedEvent) {
            routesAdded.incrementAndGet();
        } else if (eventObject instanceof RouteRemovedEvent) {
            routesAdded.decrementAndGet();
        } else if (eventObject instanceof RouteStartedEvent) {
            routesRunning.incrementAndGet();
        } else if (eventObject instanceof RouteStoppedEvent) {
            routesRunning.decrementAndGet();
        }
    }
}
