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
package org.apache.camel.impl.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;

@DevConsole("event")
@Configurer(bootstrap = true)
public class EventConsole extends AbstractDevConsole {

    @Metadata(defaultValue = "25",
              description = "Maximum capacity of last number of events to capture (capacity must be between 25 and 1000)")
    private int capacity = 25;

    private CamelEvent[] events;
    private final AtomicInteger posEvents = new AtomicInteger();
    private CamelEvent.RouteEvent[] routeEvents;
    private final AtomicInteger posRoutes = new AtomicInteger();
    private CamelEvent.ExchangeEvent[] exchangeEvents;
    private final AtomicInteger posExchanges = new AtomicInteger();
    private final ConsoleEventNotifier listener = new ConsoleEventNotifier();

    public EventConsole() {
        super("camel", "event", "Camel Events", "The most recent Camel events");
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    protected void doInit() throws Exception {
        if (capacity > 1000 || capacity < 25) {
            throw new IllegalArgumentException("Capacity must be between 25 and 1000");
        }
        this.events = new CamelEvent[capacity];
        this.routeEvents = new CamelEvent.RouteEvent[capacity];
        this.exchangeEvents = new CamelEvent.ExchangeEvent[capacity];
    }

    @Override
    protected void doStart() throws Exception {
        getCamelContext().getManagementStrategy().addEventNotifier(listener);
    }

    @Override
    protected void doStop() throws Exception {
        getCamelContext().getManagementStrategy().removeEventNotifier(listener);
    }

    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        int pos = posEvents.get();
        sb.append(appendTextEvents(events, "Camel", pos, capacity));
        sb.append("\n");
        pos = posRoutes.get();
        sb.append(appendTextEvents(routeEvents, "Route", pos, capacity));
        sb.append("\n");
        pos = posExchanges.get();
        sb.append(appendTextEvents(exchangeEvents, "Exchange", pos, capacity));
        sb.append("\n");

        return sb.toString();
    }

    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        int pos = posEvents.get();
        List<JsonObject> arr = appendJSonEvents(events, pos, capacity);
        if (!arr.isEmpty()) {
            root.put("events", arr);
        }
        pos = posRoutes.get();
        arr = appendJSonEvents(routeEvents, pos, capacity);
        if (!arr.isEmpty()) {
            root.put("routeEvents", arr);
        }
        pos = posExchanges.get();
        arr = appendJSonEvents(exchangeEvents, pos, capacity);
        if (!arr.isEmpty()) {
            root.put("exchangeEvents", arr);
        }

        return root;
    }

    private static String appendTextEvents(CamelEvent[] events, String kind, int cursor, int capacity) {
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        int added = 0;
        // cursor is at last event, so move to back
        cursor = ++cursor % capacity;
        CamelEvent event = events[cursor];
        while (pos < capacity) {
            if (event != null) {
                added++;
                if (event.getTimestamp() > 0) {
                    sb.append(String.format("    %s (age: %s)\n", event, TimeUtils.printSince(event.getTimestamp())));
                } else {
                    sb.append(String.format("    %s\n", event));
                }
            }
            // move to next
            pos++;
            cursor = ++cursor % capacity;
            event = events[cursor];
        }
        if (added > 0) {
            sb.insert(0, String.format("Last %s %s Events:\n", added, kind));
        }
        return sb.toString();
    }

    private static List<JsonObject> appendJSonEvents(CamelEvent[] events, int cursor, int capacity) {
        List<JsonObject> arr = new ArrayList<>();
        int pos = 0;
        // cursor is at last event, so move to back
        cursor = ++cursor % capacity;
        CamelEvent event = events[cursor];
        while (pos < capacity) {
            if (event != null) {
                JsonObject jo = new JsonObject();
                jo.put("type", event.getType().toString());
                if (event.getTimestamp() > 0) {
                    jo.put("timestamp", event.getTimestamp());
                }
                if (event instanceof CamelEvent.ExchangeEvent) {
                    CamelEvent.ExchangeEvent ee = (CamelEvent.ExchangeEvent) event;
                    jo.put("exchangeId", ee.getExchange().getExchangeId());
                }
                jo.put("message", event.toString());
                arr.add(jo);
            }
            // move to next
            pos++;
            cursor = ++cursor % capacity;
            event = events[cursor];
        }
        return arr;
    }

    private class ConsoleEventNotifier extends EventNotifierSupport {

        @Override
        public void notify(CamelEvent event) throws Exception {
            if (event instanceof CamelEvent.ExchangeEvent) {
                CamelEvent.ExchangeEvent ce = (CamelEvent.ExchangeEvent) event;
                int pos = posExchanges.getAndUpdate(operand -> ++operand % capacity);
                exchangeEvents[pos] = ce;
            } else if (event instanceof CamelEvent.RouteEvent) {
                CamelEvent.RouteEvent re = (CamelEvent.RouteEvent) event;
                int pos = posRoutes.getAndUpdate(operand -> ++operand % capacity);
                routeEvents[pos] = re;
            } else {
                int pos = posEvents.getAndUpdate(operand -> ++operand % capacity);
                events[pos] = event;
            }
        }

    }
}
