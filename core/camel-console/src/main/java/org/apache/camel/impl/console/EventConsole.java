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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;

@DevConsole("event")
@Configurer(bootstrap = true)
public class EventConsole extends AbstractDevConsole {

    @Metadata(defaultValue = "25", description = "Maximum capacity of last number of events to capture")
    private int capacity = 25;

    private Queue<CamelEvent> events;
    private Queue<CamelEvent.RouteEvent> routeEvents;
    private Queue<CamelEvent.ExchangeEvent> exchangeEvents;
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
        this.events = new ArrayDeque<>(capacity);
        this.routeEvents = new ArrayDeque<>(capacity);
        this.exchangeEvents = new ArrayDeque<>(capacity);
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

        if (events != null && !events.isEmpty()) {
            sb.append(String.format("Last %s Camel Events:", events.size()));
            for (CamelEvent event : events) {
                if (event.getTimestamp() > 0) {
                    sb.append(String.format("\n    %s (age: %s)", event, TimeUtils.printSince(event.getTimestamp())));
                } else {
                    sb.append(String.format("\n    %s", event));
                }
            }
            sb.append("\n");
        }
        if (routeEvents != null && !routeEvents.isEmpty()) {
            sb.append("\n");
            sb.append(String.format("Last %s Route Events:", routeEvents.size()));
            for (CamelEvent.RouteEvent event : routeEvents) {
                if (event.getTimestamp() > 0) {
                    sb.append(String.format("\n    %s (age: %s)", event, TimeUtils.printSince(event.getTimestamp())));
                } else {
                    sb.append(String.format("\n    %s", event));
                }
            }
            sb.append("\n");
        }
        if (exchangeEvents != null && !exchangeEvents.isEmpty()) {
            sb.append("\n");
            sb.append(String.format("Last %s Exchange Events:", exchangeEvents.size()));
            for (CamelEvent.ExchangeEvent event : exchangeEvents) {
                if (event.getTimestamp() > 0) {
                    sb.append(String.format("\n    %s (age: %s)", event, TimeUtils.printSince(event.getTimestamp())));
                } else {
                    sb.append(String.format("\n    %s", event));
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        if (events != null && !events.isEmpty()) {
            List<JsonObject> arr = new ArrayList<>();
            for (CamelEvent event : events) {
                JsonObject jo = new JsonObject();
                jo.put("type", event.getType().toString());
                if (event.getTimestamp() > 0) {
                    jo.put("timestamp", event.getTimestamp());
                }
                jo.put("message", event.toString());
                arr.add(jo);
            }
            root.put("events", arr);
        }
        if (routeEvents != null && !routeEvents.isEmpty()) {
            List<JsonObject> arr = new ArrayList<>();
            for (CamelEvent event : routeEvents) {
                JsonObject jo = new JsonObject();
                jo.put("type", event.getType().toString());
                if (event.getTimestamp() > 0) {
                    jo.put("timestamp", event.getTimestamp());
                }
                jo.put("message", event.toString());
                arr.add(jo);
            }
            root.put("routeEvents", arr);
        }
        if (exchangeEvents != null && !exchangeEvents.isEmpty()) {
            List<JsonObject> arr = new ArrayList<>();
            for (CamelEvent.ExchangeEvent event : exchangeEvents) {
                JsonObject jo = new JsonObject();
                jo.put("type", event.getType().toString());
                if (event.getTimestamp() > 0) {
                    jo.put("timestamp", event.getTimestamp());
                }
                jo.put("exchangeId", event.getExchange().getExchangeId());
                jo.put("message", event.toString());
                arr.add(jo);
            }
            root.put("exchangeEvents", arr);
        }

        return root;
    }

    private class ConsoleEventNotifier extends EventNotifierSupport {

        @Override
        public void notify(CamelEvent event) throws Exception {
            if (event instanceof CamelEvent.ExchangeEvent) {
                if (exchangeEvents.size() >= capacity) {
                    exchangeEvents.poll();
                }
                exchangeEvents.add((CamelEvent.ExchangeEvent) event);
            } else if (event instanceof CamelEvent.RouteEvent) {
                if (routeEvents.size() >= capacity) {
                    routeEvents.poll();
                }
                routeEvents.add((CamelEvent.RouteEvent) event);
            } else {
                if (events.size() >= capacity) {
                    events.poll();
                }
                events.offer(event);
            }
        }

    }
}
