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
import static org.apache.camel.component.micrometer.MicrometerConstants.CAMEL_CONTEXT_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_ROUTES_ADDED;
import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_ROUTES_RUNNING;
import static org.apache.camel.component.micrometer.MicrometerConstants.EVENT_TYPE_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.SERVICE_NAME;


public class MicrometerRouteEventNotifier extends AbstractMicrometerEventNotifier<AbstractRouteEvent> {

    private final AtomicLong routesAdded = new AtomicLong();
    private final AtomicLong routesRunning = new AtomicLong();

    public MicrometerRouteEventNotifier() {
        super(AbstractRouteEvent.class);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        Gauge.builder(DEFAULT_CAMEL_ROUTES_ADDED, routesAdded, value -> Long.valueOf(value.get()).doubleValue())
                .tag(SERVICE_NAME, MicrometerEventNotifierService.class.getSimpleName())
                .tag(CAMEL_CONTEXT_TAG, getCamelContext().getName())
                .tag(EVENT_TYPE_TAG, AbstractRouteEvent.class.getSimpleName())
                .register(getMeterRegistry());
        Gauge.builder(DEFAULT_CAMEL_ROUTES_RUNNING, routesRunning, value -> Long.valueOf(value.get()).doubleValue())
                .tag(SERVICE_NAME, MicrometerEventNotifierService.class.getSimpleName())
                .tag(CAMEL_CONTEXT_TAG, getCamelContext().getName())
                .tag(EVENT_TYPE_TAG, AbstractRouteEvent.class.getSimpleName())
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
