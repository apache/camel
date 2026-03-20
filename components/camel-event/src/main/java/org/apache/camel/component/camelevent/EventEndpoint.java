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
package org.apache.camel.component.camelevent;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Subscribe to Camel internal events such as route started/stopped and exchange completed/failed.
 *
 * The URI path specifies comma-separated event types to subscribe to. Event types correspond to
 * {@link org.apache.camel.spi.CamelEvent.Type} enum values (case-insensitive), for example:
 * {@code event:RouteStarted,RouteStopped} or {@code event:ExchangeCompleted?filter=myRouteId}.
 *
 * Wildcard patterns are supported using a {@code *} suffix, for example: {@code event:Route*} matches all route events,
 * {@code event:Exchange*} matches all exchange events, and {@code event:*} matches all events.
 */
@UriEndpoint(firstVersion = "4.19.0", scheme = "event", title = "Event", syntax = "event:events",
             consumerOnly = true, remote = false,
             category = { Category.CORE, Category.MONITORING })
public class EventEndpoint extends DefaultEndpoint {

    @UriPath(description = "Comma-separated list of event types to subscribe to."
                           + " Event types correspond to CamelEvent.Type enum values (case-insensitive),"
                           + " for example: RouteStarted, RouteStopped, ExchangeCompleted, ExchangeFailed."
                           + " Wildcard patterns are supported using a * suffix,"
                           + " for example: Route* matches all route events, Exchange* matches all exchange events,"
                           + " and * matches all events.")
    @Metadata(required = true)
    private String events;

    @UriParam(description = "Comma-separated list of filters to narrow down events."
                            + " For route events, this filters by route ID."
                            + " For exchange events, this filters by the route ID of the exchange.")
    private String filter;

    private Set<CamelEvent.Type> eventTypes;
    private Set<String> filterValues;

    public EventEndpoint() {
    }

    public EventEndpoint(String uri, Component component, String events) {
        super(uri, component);
        this.events = events;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new RuntimeCamelException("Cannot produce to an EventEndpoint: " + getEndpointUri());
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        EventConsumer consumer = new EventConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        // Parse event types (with wildcard support)
        if (events != null && !events.isEmpty()) {
            Set<CamelEvent.Type> types = new LinkedHashSet<>();
            for (String token : events.split(",")) {
                String name = token.trim();
                if (!name.isEmpty()) {
                    if (name.contains("*")) {
                        types.addAll(resolveWildcard(name));
                    } else {
                        types.add(resolveEventType(name));
                    }
                }
            }
            eventTypes = Collections.unmodifiableSet(types);
        } else {
            eventTypes = Collections.emptySet();
        }
        // Parse filter values
        if (filter != null && !filter.isEmpty()) {
            filterValues = Arrays.stream(filter.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        } else {
            filterValues = Collections.emptySet();
        }
    }

    /**
     * Resolves an event type name (case-insensitive) to a {@link CamelEvent.Type} enum value.
     */
    static CamelEvent.Type resolveEventType(String name) {
        for (CamelEvent.Type type : CamelEvent.Type.values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException(
                "Unknown event type: " + name + ". Known types: "
                                           + Arrays.toString(CamelEvent.Type.values()));
    }

    /**
     * Resolves a wildcard pattern (e.g., "Route*", "Exchange*", "*") to the matching {@link CamelEvent.Type} values.
     */
    static Set<CamelEvent.Type> resolveWildcard(String pattern) {
        String prefix = pattern.substring(0, pattern.indexOf('*')).toLowerCase();
        Set<CamelEvent.Type> matched = new LinkedHashSet<>();
        for (CamelEvent.Type type : CamelEvent.Type.values()) {
            if (type.name().toLowerCase().startsWith(prefix)) {
                matched.add(type);
            }
        }
        if (matched.isEmpty()) {
            throw new IllegalArgumentException(
                    "No event types match wildcard pattern: " + pattern + ". Known types: "
                                               + Arrays.toString(CamelEvent.Type.values()));
        }
        return matched;
    }

    public String getEvents() {
        return events;
    }

    /**
     * Comma-separated list of event types to subscribe to. Event types correspond to CamelEvent.Type enum values
     * (case-insensitive), for example: RouteStarted, RouteStopped, ExchangeCompleted, ExchangeFailed. Wildcard patterns
     * are supported using a * suffix, for example: Route* matches all route events, Exchange* matches all exchange
     * events, and * matches all events.
     */
    public void setEvents(String events) {
        this.events = events;
    }

    public String getFilter() {
        return filter;
    }

    /**
     * Comma-separated list of filters to narrow down events. For route events, this filters by route ID. For exchange
     * events, this filters by the route ID of the exchange.
     */
    public void setFilter(String filter) {
        this.filter = filter;
    }

    public Set<CamelEvent.Type> getEventTypes() {
        return eventTypes;
    }

    public Set<String> getFilterValues() {
        return filterValues;
    }
}
