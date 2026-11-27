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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Receive the internal events of the CamelContext, such as a route that started or an exchange that failed.
 *
 * The URI path specifies comma-separated event types to subscribe to. Event types correspond to
 * {@link org.apache.camel.spi.CamelEvent.Type} enum values (case-insensitive), for example:
 * {@code event:RouteStarted,RouteStopped} or {@code event:ExchangeCompleted?include=myRouteId}.
 *
 * Wildcard patterns are supported using a {@code *} suffix, for example: {@code event:Route*} matches all route events,
 * {@code event:Exchange*} matches all exchange events, and {@code event:*} matches all events.
 */
@UriEndpoint(firstVersion = "4.19.0", scheme = "event", title = "Event", syntax = "event:events",
             consumerOnly = true, remote = false, internal = true,
             category = { Category.CORE, Category.MONITORING },
             headersClass = CamelEventConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO, javaType = "org.apache.camel.spi.CamelEvent",
                                   dependsOn = "batchSize",
                                   description = "The event, such as a RouteStartedEvent or an ExchangeCompletedEvent. With a"
                                                 + " batchSize above 1, a List of the events"))
public class CamelEventEndpoint extends DefaultEndpoint {

    @UriPath(description = "A comma-separated list of the event types to consume, as names of the CamelEvent.Type enum,"
                           + " ignoring case, for example RouteStarted,ExchangeFailed. A name that ends with * matches every"
                           + " type that starts with the text before it: Route* matches RouteStarted and also RoutesStarted,"
                           + " and * matches all types. An unknown name fails the creation of the endpoint.")
    @Metadata(required = true)
    private String events;

    @UriParam(description = "A comma-separated list of route IDs. The consumer drops a route event of another route, and an"
                            + " exchange event of an exchange that another route created. Events without a route, such as"
                            + " CamelContext and service events, are always consumed.")
    private String include;

    @UriParam(description = "A comma-separated list of route IDs. The consumer drops a route event of one of these routes,"
                            + " and an exchange event of an exchange that one of these routes created. It applies after the"
                            + " include option, and does not affect events without a route.")
    private String exclude;

    @UriParam(description = "The fully qualified name of an event class. The consumer drops an event that is not an instance"
                            + " of this class, in addition to the event types of the path. Use it with the Custom event type to"
                            + " consume only your own events of this class.")
    private String customEventClass;

    @UriParam(description = "Whether to put the events in a bounded queue that a pool of threads routes, so the thread that"
                            + " fires an event does not wait for the route. If false, the event is routed on the thread that"
                            + " fires it.",
              defaultValue = "false")
    private boolean async;

    @UriParam(description = "The number of threads that take the events from the queue and route them. Only used when async"
                            + " is true.",
              defaultValue = "10")
    private int asyncPoolSize = 10;

    @UriParam(description = "The capacity of the bounded event queue used when async is enabled."
                            + " When the queue is full, the backpressure policy determines the behavior."
                            + " Only used when the async option is enabled.",
              defaultValue = "1000")
    private int asyncQueueSize = 1000;

    @UriParam(description = "What happens to an event when the async queue is full. Block makes the thread that fires the"
                            + " event wait for space, Drop discards the event with a DEBUG log, and Fail throws an exception,"
                            + " which Camel logs at WARN and ignores, so the event is lost.",
              defaultValue = "Block")
    private BackpressurePolicy backpressurePolicy = BackpressurePolicy.Block;

    @UriParam(description = "The number of events to collect into one exchange, whose body is a java.util.List of the events."
                            + " A batch is sent when it is full, and every batchTimeout with the events collected so far. With 0"
                            + " or 1, each event is its own exchange, with the event as the body.",
              defaultValue = "0")
    private int batchSize;

    @UriParam(description = "The interval in milliseconds at which a timer sends the events collected so far, even if the batch"
                            + " is not full. Only used when batchSize is greater than 1.",
              defaultValue = "1000")
    private long batchTimeout = 1000;

    private Set<CamelEvent.Type> eventTypes;
    private Set<String> includeValues;
    private Set<String> excludeValues;
    private Class<?> customEventClazz;

    public CamelEventEndpoint() {
    }

    public CamelEventEndpoint(String uri, Component component, String events) {
        super(uri, component);
        this.events = events;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new RuntimeCamelException("Cannot produce to a CamelEventEndpoint: " + getEndpointUri());
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        CamelEventConsumer consumer = new CamelEventConsumer(this, processor);
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
        // Parse include values
        if (include != null && !include.isEmpty()) {
            includeValues = Arrays.stream(include.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        } else {
            includeValues = Collections.emptySet();
        }
        // Parse exclude values
        if (exclude != null && !exclude.isEmpty()) {
            excludeValues = Arrays.stream(exclude.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        } else {
            excludeValues = Collections.emptySet();
        }
        // Resolve custom event class
        if (customEventClass != null && !customEventClass.isEmpty()) {
            customEventClazz = getCamelContext().getClassResolver().resolveMandatoryClass(customEventClass);
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

    public void setEvents(String events) {
        this.events = events;
    }

    public String getInclude() {
        return include;
    }

    public void setInclude(String include) {
        this.include = include;
    }

    public String getExclude() {
        return exclude;
    }

    public void setExclude(String exclude) {
        this.exclude = exclude;
    }

    public String getCustomEventClass() {
        return customEventClass;
    }

    public void setCustomEventClass(String customEventClass) {
        this.customEventClass = customEventClass;
    }

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public int getAsyncPoolSize() {
        return asyncPoolSize;
    }

    public void setAsyncPoolSize(int asyncPoolSize) {
        this.asyncPoolSize = asyncPoolSize;
    }

    public int getAsyncQueueSize() {
        return asyncQueueSize;
    }

    public void setAsyncQueueSize(int asyncQueueSize) {
        this.asyncQueueSize = asyncQueueSize;
    }

    public BackpressurePolicy getBackpressurePolicy() {
        return backpressurePolicy;
    }

    public void setBackpressurePolicy(BackpressurePolicy backpressurePolicy) {
        this.backpressurePolicy = backpressurePolicy;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getBatchTimeout() {
        return batchTimeout;
    }

    public void setBatchTimeout(long batchTimeout) {
        this.batchTimeout = batchTimeout;
    }

    public Set<CamelEvent.Type> getEventTypes() {
        return eventTypes;
    }

    public Set<String> getIncludeValues() {
        return includeValues;
    }

    public Set<String> getExcludeValues() {
        return excludeValues;
    }

    public Class<?> getCustomEventClazz() {
        return customEventClazz;
    }
}
