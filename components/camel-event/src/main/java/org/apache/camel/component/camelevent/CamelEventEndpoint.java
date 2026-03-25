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
 * {@code event:RouteStarted,RouteStopped} or {@code event:ExchangeCompleted?include=myRouteId}.
 *
 * Wildcard patterns are supported using a {@code *} suffix, for example: {@code event:Route*} matches all route events,
 * {@code event:Exchange*} matches all exchange events, and {@code event:*} matches all events.
 */
@UriEndpoint(firstVersion = "4.19.0", scheme = "event", title = "Event", syntax = "event:events",
             consumerOnly = true, remote = false,
             category = { Category.CORE, Category.MONITORING },
             headersClass = CamelEventConstants.class)
public class CamelEventEndpoint extends DefaultEndpoint {

    @UriPath(description = "Comma-separated list of event types to subscribe to."
                           + " Event types correspond to CamelEvent.Type enum values (case-insensitive),"
                           + " for example: RouteStarted, RouteStopped, ExchangeCompleted, ExchangeFailed."
                           + " Wildcard patterns are supported using a * suffix,"
                           + " for example: Route* matches all route events, Exchange* matches all exchange events,"
                           + " and * matches all events.")
    @Metadata(required = true)
    private String events;

    @UriParam(description = "Comma-separated list of route IDs to include."
                            + " For route events, this matches the route ID."
                            + " For exchange events, this matches the route ID of the exchange."
                            + " Only events matching one of the specified route IDs will be accepted.")
    private String include;

    @UriParam(description = "Comma-separated list of route IDs to exclude."
                            + " For route events, this excludes by route ID."
                            + " For exchange events, this excludes by the route ID of the exchange."
                            + " Events matching any of the specified route IDs will be rejected."
                            + " This option can be used together with the include option.")
    private String exclude;

    @UriParam(description = "Fully qualified class name of a custom event class to filter on."
                            + " When set, only events that are instances of the specified class will be accepted."
                            + " This is useful for subscribing to custom user-defined events.")
    private String customEventClass;

    @UriParam(description = "Whether to process events asynchronously using a thread pool."
                            + " When enabled, the event notifier thread is not blocked while the event exchange is processed."
                            + " Use asyncPoolSize to control the maximum number of concurrent event processing threads.",
              defaultValue = "false")
    private boolean async;

    @UriParam(description = "The maximum number of threads in the pool for async event processing."
                            + " Only used when the async option is enabled.",
              defaultValue = "10")
    private int asyncPoolSize = 10;

    @UriParam(description = "The capacity of the bounded event queue used when async is enabled."
                            + " When the queue is full, the backpressure policy determines the behavior."
                            + " Only used when the async option is enabled.",
              defaultValue = "1000")
    private int asyncQueueSize = 1000;

    @UriParam(description = "The backpressure policy when the async event queue is full."
                            + " Supported values: Block (block the event notifier thread until space is available),"
                            + " Drop (silently discard the event), Fail (throw an exception).",
              defaultValue = "Block")
    private BackpressurePolicy backpressurePolicy = BackpressurePolicy.Block;

    @UriParam(description = "Enables event batching. When set to a value greater than 1, events are collected"
                            + " into a java.util.List and dispatched as a single exchange when the batch is full"
                            + " or the batchTimeout expires. The exchange body will be a List<CamelEvent>.",
              defaultValue = "0")
    private int batchSize;

    @UriParam(description = "The maximum time in milliseconds to wait for a batch to fill before dispatching"
                            + " a partial batch. Only used when batchSize is greater than 1.",
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

    /**
     * Comma-separated list of event types to subscribe to. Event types correspond to CamelEvent.Type enum values
     * (case-insensitive), for example: RouteStarted, RouteStopped, ExchangeCompleted, ExchangeFailed. Wildcard patterns
     * are supported using a * suffix, for example: Route* matches all route events, Exchange* matches all exchange
     * events, and * matches all events.
     */
    public void setEvents(String events) {
        this.events = events;
    }

    public String getInclude() {
        return include;
    }

    /**
     * Comma-separated list of route IDs to include. For route events, this matches the route ID. For exchange events,
     * this matches the route ID of the exchange. Only events matching one of the specified route IDs will be accepted.
     */
    public void setInclude(String include) {
        this.include = include;
    }

    public String getExclude() {
        return exclude;
    }

    /**
     * Comma-separated list of route IDs to exclude. For route events, this excludes by route ID. For exchange events,
     * this excludes by the route ID of the exchange. Events matching any of the specified route IDs will be rejected.
     * This option can be used together with the include option.
     */
    public void setExclude(String exclude) {
        this.exclude = exclude;
    }

    public String getCustomEventClass() {
        return customEventClass;
    }

    /**
     * Fully qualified class name of a custom event class to filter on. When set, only events that are instances of the
     * specified class will be accepted. This is useful for subscribing to custom user-defined events.
     */
    public void setCustomEventClass(String customEventClass) {
        this.customEventClass = customEventClass;
    }

    public boolean isAsync() {
        return async;
    }

    /**
     * Whether to process events asynchronously using a thread pool. When enabled, the event notifier thread is not
     * blocked while the event exchange is processed. Use asyncPoolSize to control the maximum number of concurrent
     * event processing threads.
     */
    public void setAsync(boolean async) {
        this.async = async;
    }

    public int getAsyncPoolSize() {
        return asyncPoolSize;
    }

    /**
     * The maximum number of threads in the pool for async event processing. Only used when the async option is enabled.
     */
    public void setAsyncPoolSize(int asyncPoolSize) {
        this.asyncPoolSize = asyncPoolSize;
    }

    public int getAsyncQueueSize() {
        return asyncQueueSize;
    }

    /**
     * The capacity of the bounded event queue used when async is enabled. When the queue is full, the backpressure
     * policy determines the behavior. Only used when the async option is enabled.
     */
    public void setAsyncQueueSize(int asyncQueueSize) {
        this.asyncQueueSize = asyncQueueSize;
    }

    public BackpressurePolicy getBackpressurePolicy() {
        return backpressurePolicy;
    }

    /**
     * The backpressure policy when the async event queue is full. Supported values: Block (block the event notifier
     * thread until space is available), Drop (silently discard the event), Fail (throw an exception).
     */
    public void setBackpressurePolicy(BackpressurePolicy backpressurePolicy) {
        this.backpressurePolicy = backpressurePolicy;
    }

    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Enables event batching. When set to a value greater than 1, events are collected into a java.util.List and
     * dispatched as a single exchange when the batch is full or the batchTimeout expires. The exchange body will be a
     * List&lt;CamelEvent&gt;.
     */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getBatchTimeout() {
        return batchTimeout;
    }

    /**
     * The maximum time in milliseconds to wait for a batch to fill before dispatching a partial batch. Only used when
     * batchSize is greater than 1.
     */
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
