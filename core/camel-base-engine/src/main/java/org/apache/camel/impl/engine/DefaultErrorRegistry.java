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
package org.apache.camel.impl.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.MessageHistory;
import org.apache.camel.spi.BacklogErrorEventMessage;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.ErrorRegistry;
import org.apache.camel.spi.ErrorRegistryView;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsonable;
import org.apache.camel.util.json.Jsoner;

/**
 * Default {@link ErrorRegistry} implementation that listens to exchange failure events and captures error snapshots.
 */
public class DefaultErrorRegistry extends EventNotifierSupport implements ErrorRegistry {

    private final ConcurrentLinkedDeque<BacklogErrorEventMessage> entries = new ConcurrentLinkedDeque<>();
    private final AtomicLong uidCounter = new AtomicLong();
    private volatile boolean enabled;
    private volatile int maximumEntries = 100;
    private volatile Duration timeToLive = Duration.ofHours(1);
    private volatile int bodyMaxChars = 32 * 1024;
    private volatile boolean bodyIncludeStreams;
    private volatile boolean bodyIncludeFiles = true;
    private volatile boolean includeExchangeProperties = true;
    private volatile boolean includeExchangeVariables = true;

    public DefaultErrorRegistry() {
        setIgnoreCamelContextEvents(true);
        setIgnoreCamelContextInitEvents(true);
        setIgnoreRouteEvents(true);
        setIgnoreServiceEvents(true);
        setIgnoreExchangeEvents(true);
        setIgnoreExchangeCreatedEvent(true);
        setIgnoreExchangeCompletedEvent(true);
        setIgnoreExchangeFailedEvents(true);
        setIgnoreExchangeRedeliveryEvents(true);
        setIgnoreExchangeSentEvents(true);
        setIgnoreExchangeSendingEvents(true);
        setIgnoreExchangeAsyncProcessingStartedEvents(true);
        setIgnoreStepEvents(true);
    }

    @Override
    public void notify(CamelEvent event) throws Exception {
        if (!enabled) {
            return;
        }
        if (event instanceof CamelEvent.ExchangeFailedEvent e) {
            capture(e.getExchange(), false);
        } else if (event instanceof CamelEvent.ExchangeFailureHandledEvent e) {
            capture(e.getExchange(), true);
        }
    }

    @Override
    public boolean isEnabled(CamelEvent event) {
        return enabled;
    }

    @Override
    public boolean isDisabled() {
        return !enabled;
    }

    @SuppressWarnings("unchecked")
    private void capture(Exchange exchange, boolean handled) {
        Throwable exception;
        if (handled) {
            exception = exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Throwable.class);
        } else {
            exception = exchange.getException();
        }
        if (exception == null) {
            return;
        }

        long uid = uidCounter.incrementAndGet();
        long timestamp = System.currentTimeMillis();
        String exchangeId = exchange.getExchangeId();
        String routeId = exchange.getProperty(ExchangePropertyKey.FAILURE_ROUTE_ID, String.class);
        if (routeId == null) {
            routeId = exchange.getFromRouteId();
        }
        String fromRouteId = exchange.getFromRouteId();
        String routeGroup = null;
        if (routeId != null) {
            org.apache.camel.Route route = exchange.getContext().getRoute(routeId);
            if (route != null) {
                routeGroup = route.getGroup();
            }
        }
        String endpointUri = exchange.getProperty(ExchangePropertyKey.FAILURE_ENDPOINT, String.class);

        // capture node id and location from the last message history entry
        // (the historyNodeId on the exchange extension is cleared after the node finishes processing,
        // so by the time the error event fires it is always null)
        String toNode = null;
        String location = null;
        List<MessageHistory> history
                = exchange.getProperty(ExchangePropertyKey.MESSAGE_HISTORY, List.class);
        if (history != null && !history.isEmpty()) {
            MessageHistory last = history.get(history.size() - 1);
            if (last.getNode() != null) {
                toNode = last.getNode().getId();
                location = LoggerHelper.getLineNumberLoggerName(last.getNode());
            }
        }

        // capture step id (set by Step EIP)
        String stepId = exchange.getProperty(ExchangePropertyKey.STEP_ID, String.class);

        // capture from endpoint URI
        String fromEndpointUri = null;
        if (exchange.getFromEndpoint() != null) {
            fromEndpointUri = exchange.getFromEndpoint().getEndpointUri();
        }

        // capture route uptime
        long routeUptime = 0;
        if (routeId != null) {
            org.apache.camel.Route r = exchange.getContext().getRoute(routeId);
            if (r != null) {
                routeUptime = r.getUptimeMillis();
            }
        }

        // capture exchange elapsed time
        long elapsed = exchange.getClock().elapsed();

        // capture exchange data snapshot
        JsonObject data = MessageHelper.dumpAsJSonObject(
                exchange.getMessage(),
                includeExchangeProperties, includeExchangeVariables,
                true, true,
                bodyIncludeStreams, bodyIncludeFiles, bodyMaxChars);

        // capture message history
        String[] messageHistory = captureMessageHistory(exchange);

        String threadName = Thread.currentThread().getName();

        DefaultBacklogErrorEventMessage entry = new DefaultBacklogErrorEventMessage(
                uid, timestamp, location, routeId, fromRouteId, routeGroup, exchangeId,
                endpointUri, toNode, stepId, fromEndpointUri, routeUptime, elapsed,
                threadName, data, exception, handled, messageHistory);

        // deduplicate: if the same exchange already has an entry, replace it
        // (this happens with circuit breaker where the inner failure is handled first,
        // then the outer error handler captures the same exchange again)
        entries.removeIf(e -> exchangeId.equals(e.getExchangeId()));
        entries.addFirst(entry);
        evict();
    }

    @SuppressWarnings("unchecked")
    private static String[] captureMessageHistory(Exchange exchange) {
        List<MessageHistory> history
                = exchange.getProperty(ExchangePropertyKey.MESSAGE_HISTORY, List.class);
        if (history == null || history.isEmpty()) {
            return null;
        }
        String[] result = new String[history.size()];
        for (int i = 0; i < history.size(); i++) {
            MessageHistory mh = history.get(i);
            String nodeId = mh.getNode() != null ? mh.getNode().getId() : null;
            long elapsed = mh.getElapsed();
            if (elapsed > 0) {
                result[i] = mh.getRouteId() + "[" + nodeId + "] (" + elapsed + " ms)";
            } else {
                result[i] = mh.getRouteId() + "[" + nodeId + "]";
            }
        }
        return result;
    }

    private void evict() {
        while (entries.size() > maximumEntries) {
            entries.pollLast();
        }
        Instant cutoff = Instant.now().minus(timeToLive);
        while (!entries.isEmpty()) {
            BacklogErrorEventMessage last = entries.peekLast();
            if (last != null && Instant.ofEpochMilli(last.getTimestamp()).isBefore(cutoff)) {
                entries.pollLast();
            } else {
                break;
            }
        }
    }

    // -- View methods (global scope) --

    @Override
    public int size() {
        evict();
        return entries.size();
    }

    @Override
    public Collection<BacklogErrorEventMessage> browse() {
        return browse(-1);
    }

    @Override
    public Collection<BacklogErrorEventMessage> browse(int limit) {
        evict();
        if (limit <= 0) {
            return Collections.unmodifiableList(new ArrayList<>(entries));
        }
        List<BacklogErrorEventMessage> result = new ArrayList<>(Math.min(limit, entries.size()));
        int count = 0;
        for (BacklogErrorEventMessage entry : entries) {
            if (count >= limit) {
                break;
            }
            result.add(entry);
            count++;
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public void clear() {
        entries.clear();
    }

    // -- Scoped view --

    @Override
    public ErrorRegistryView forRoute(String routeId) {
        return new RouteView(routeId);
    }

    // -- Configuration --

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        setIgnoreExchangeEvents(!enabled);
        setIgnoreExchangeFailedEvents(!enabled);
        if (enabled && getCamelContext() != null) {
            getCamelContext().getCamelContextExtension().setEventNotificationApplicable(true);
        }
    }

    @Override
    public int getMaximumEntries() {
        return maximumEntries;
    }

    @Override
    public void setMaximumEntries(int maximumEntries) {
        this.maximumEntries = maximumEntries;
    }

    @Override
    public Duration getTimeToLive() {
        return timeToLive;
    }

    @Override
    public void setTimeToLive(Duration timeToLive) {
        this.timeToLive = timeToLive;
    }

    @Override
    public int getBodyMaxChars() {
        return bodyMaxChars;
    }

    @Override
    public void setBodyMaxChars(int bodyMaxChars) {
        this.bodyMaxChars = bodyMaxChars;
    }

    @Override
    public boolean isBodyIncludeStreams() {
        return bodyIncludeStreams;
    }

    @Override
    public void setBodyIncludeStreams(boolean bodyIncludeStreams) {
        this.bodyIncludeStreams = bodyIncludeStreams;
    }

    @Override
    public boolean isBodyIncludeFiles() {
        return bodyIncludeFiles;
    }

    @Override
    public void setBodyIncludeFiles(boolean bodyIncludeFiles) {
        this.bodyIncludeFiles = bodyIncludeFiles;
    }

    @Override
    public boolean isIncludeExchangeProperties() {
        return includeExchangeProperties;
    }

    @Override
    public void setIncludeExchangeProperties(boolean includeExchangeProperties) {
        this.includeExchangeProperties = includeExchangeProperties;
    }

    @Override
    public boolean isIncludeExchangeVariables() {
        return includeExchangeVariables;
    }

    @Override
    public void setIncludeExchangeVariables(boolean includeExchangeVariables) {
        this.includeExchangeVariables = includeExchangeVariables;
    }

    /**
     * A filtered view over entries for a specific route.
     */
    private class RouteView implements ErrorRegistryView {

        private final String routeId;

        RouteView(String routeId) {
            this.routeId = Objects.requireNonNull(routeId);
        }

        @Override
        public int size() {
            evict();
            int count = 0;
            for (BacklogErrorEventMessage entry : entries) {
                if (routeId.equals(entry.getRouteId())) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public Collection<BacklogErrorEventMessage> browse() {
            return browse(-1);
        }

        @Override
        public Collection<BacklogErrorEventMessage> browse(int limit) {
            evict();
            List<BacklogErrorEventMessage> result = new ArrayList<>();
            for (BacklogErrorEventMessage entry : entries) {
                if (routeId.equals(entry.getRouteId())) {
                    result.add(entry);
                    if (limit > 0 && result.size() >= limit) {
                        break;
                    }
                }
            }
            return Collections.unmodifiableList(result);
        }

        @Override
        public void clear() {
            entries.removeIf(entry -> routeId.equals(entry.getRouteId()));
        }
    }

    /**
     * Default implementation of {@link BacklogErrorEventMessage}.
     */
    static final class DefaultBacklogErrorEventMessage implements BacklogErrorEventMessage {

        private final long uid;
        private final long timestamp;
        private final String location;
        private final String routeId;
        private final String fromRouteId;
        private final String routeGroup;
        private final String exchangeId;
        private final String endpointUri;
        private final String toNode;
        private final String stepId;
        private final String fromEndpointUri;
        private final long routeUptime;
        private final long elapsed;
        private final String threadName;
        private final JsonObject data;
        private final Throwable exception;
        private final boolean handled;
        private final String[] messageHistory;

        private volatile String dataAsJson;
        private volatile String exceptionAsJSon;

        DefaultBacklogErrorEventMessage(
                                        long uid, long timestamp, String location, String routeId, String fromRouteId,
                                        String routeGroup,
                                        String exchangeId, String endpointUri, String toNode,
                                        String stepId, String fromEndpointUri, long routeUptime, long elapsed,
                                        String threadName,
                                        JsonObject data, Throwable exception, boolean handled, String[] messageHistory) {
            this.uid = uid;
            this.timestamp = timestamp;
            this.location = location;
            this.routeId = routeId;
            this.fromRouteId = fromRouteId;
            this.routeGroup = routeGroup;
            this.exchangeId = exchangeId;
            this.endpointUri = endpointUri;
            this.toNode = toNode;
            this.stepId = stepId;
            this.fromEndpointUri = fromEndpointUri;
            this.routeUptime = routeUptime;
            this.elapsed = elapsed;
            this.threadName = threadName;
            this.data = data;
            this.exception = exception;
            this.handled = handled;
            this.messageHistory = messageHistory;
        }

        @Override
        public long getUid() {
            return uid;
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String getLocation() {
            return location;
        }

        @Override
        public String getRouteId() {
            return routeId;
        }

        @Override
        public String getFromRouteId() {
            return fromRouteId;
        }

        @Override
        public String getRouteGroup() {
            return routeGroup;
        }

        @Override
        public String getExchangeId() {
            return exchangeId;
        }

        @Override
        public String getEndpointUri() {
            return endpointUri;
        }

        @Override
        public String getToNode() {
            return toNode;
        }

        @Override
        public String getStepId() {
            return stepId;
        }

        @Override
        public String getFromEndpointUri() {
            return fromEndpointUri;
        }

        @Override
        public long getRouteUptime() {
            return routeUptime;
        }

        @Override
        public long getElapsed() {
            return elapsed;
        }

        @Override
        public String getProcessingThreadName() {
            return threadName;
        }

        @Override
        public String getMessageAsJSon() {
            if (dataAsJson == null) {
                dataAsJson = data.toJson();
            }
            return dataAsJson;
        }

        @Override
        public boolean hasException() {
            return exception != null;
        }

        @Override
        public String getExceptionAsJSon() {
            if (exceptionAsJSon == null && exception != null) {
                exceptionAsJSon = MessageHelper.dumpExceptionAsJSon(exception, 4, true);
            }
            return exceptionAsJSon;
        }

        @Override
        public Throwable getException() {
            return exception;
        }

        @Override
        public boolean isHandled() {
            return handled;
        }

        @Override
        public String getExceptionType() {
            return exception.getClass().getName();
        }

        @Override
        public String getExceptionMessage() {
            return exception.getMessage();
        }

        @Override
        public String[] getMessageHistory() {
            return messageHistory != null ? messageHistory.clone() : null;
        }

        @Override
        public String toJSon(int indent) {
            Jsonable jo = (Jsonable) asJSon();
            if (indent > 0) {
                return Jsoner.prettyPrint(jo.toJson(), indent);
            } else {
                return Jsoner.prettyPrint(jo.toJson());
            }
        }

        @Override
        public Map<String, Object> asJSon() {
            JsonObject jo = new JsonObject();
            jo.put("uid", uid);
            jo.put("timestamp", timestamp);
            if (location != null) {
                jo.put("location", location);
            }
            if (routeId != null) {
                jo.put("routeId", routeId);
            }
            if (fromRouteId != null) {
                jo.put("fromRouteId", fromRouteId);
            }
            if (routeGroup != null) {
                jo.put("routeGroup", routeGroup);
            }
            if (exchangeId != null) {
                jo.put("exchangeId", exchangeId);
            }
            if (endpointUri != null) {
                jo.put("endpointUri", endpointUri);
            }
            if (toNode != null) {
                jo.put("nodeId", toNode);
            }
            if (stepId != null) {
                jo.put("stepId", stepId);
            }
            if (fromEndpointUri != null) {
                jo.put("fromEndpointUri", fromEndpointUri);
            }
            jo.put("routeUptime", routeUptime);
            jo.put("elapsed", elapsed);
            jo.put("threadName", threadName);
            jo.put("handled", handled);
            // message data
            jo.put("message", data.getMap("message"));
            // exception
            if (exception != null) {
                try {
                    JsonObject exObj = MessageHelper.dumpExceptionAsJSonObject(exception);
                    jo.put("exception", exObj.get("exception"));
                } catch (Exception e) {
                    // ignore
                }
            }
            // message history
            if (messageHistory != null) {
                jo.put("messageHistory", List.of(messageHistory));
            }
            return jo;
        }

        @Override
        public String toString() {
            return "DefaultBacklogErrorEventMessage[" + exchangeId + " at " + routeId + "]";
        }
    }
}
