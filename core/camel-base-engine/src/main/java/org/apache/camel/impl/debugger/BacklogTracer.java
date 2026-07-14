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
package org.apache.camel.impl.debugger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.NamedNode;
import org.apache.camel.NonManagedService;
import org.apache.camel.Predicate;
import org.apache.camel.Route;
import org.apache.camel.spi.BacklogTracerActivityMessage;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.Language;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * A tracer used for message tracing, storing a copy of the message details in a backlog.
 * <p/>
 * This tracer allows to store message tracers per node in the Camel routes. The tracers is stored in a backlog queue
 * (FIFO based) which allows to pull the traced messages on demand.
 */
public class BacklogTracer extends ServiceSupport implements org.apache.camel.spi.SyntheticBacklogTracer {

    // limit the tracer to a thousand messages in total
    public static final int MAX_BACKLOG_SIZE = 1000;
    private final CamelContext camelContext;
    private final Language simple;
    private boolean enabled;
    private boolean standby;
    private final AtomicLong traceCounter = new AtomicLong();
    // use a queue with an upper limit to avoid storing too many messages
    private final Queue<BacklogTracerEventMessage> queue = new LinkedBlockingQueue<>(MAX_BACKLOG_SIZE);
    // how many of the last messages to keep in the backlog at total
    private int backlogSize = 100;
    // use tracer to capture additional information for capturing latest completed exchange message-history
    private final Queue<BacklogTracerEventMessage> provisionalHistoryQueue = new LinkedBlockingQueue<>(MAX_BACKLOG_SIZE);
    private final Queue<BacklogTracerEventMessage> completeHistoryQueue = new LinkedBlockingQueue<>(MAX_BACKLOG_SIZE + 1);
    // rolling window of completed exchange activity for live monitoring
    private final Queue<BacklogTracerActivityMessage> activityQueue = new LinkedBlockingQueue<>(MAX_BACKLOG_SIZE);
    private final ConcurrentHashMap<String, DefaultBacklogTracerActivityMessage> inflightActivity = new ConcurrentHashMap<>();
    private final ActivityEventNotifier activityEventNotifier = new ActivityEventNotifier();
    private int activitySize = 100;
    private static final long INFLIGHT_EVICTION_MILLIS = 5 * 60 * 1000;
    private final Object historyLock = new Object();
    private volatile String lastCompletedBreadcrumbId;
    private boolean removeOnDump = true;
    private int bodyMaxChars = 32 * 1024;
    private boolean bodyIncludeStreams;
    private boolean bodyIncludeFiles = true;
    private boolean includeExchangeProperties = true;
    private boolean includeExchangeVariables = true;
    private boolean includeException = true;
    private boolean activityEnabled;
    private boolean traceRests;
    private boolean traceTemplates;
    // a pattern to filter tracing nodes
    private String tracePattern;
    private String[] patterns;
    private String traceFilter;
    private Predicate predicate;

    BacklogTracer(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.simple = camelContext.resolveLanguage("simple");
    }

    /**
     * Creates a new backlog tracer.
     *
     * @param  context Camel context
     * @return         a new backlog tracer
     */
    public static BacklogTracer createTracer(CamelContext context) {
        return new BacklogTracer(context);
    }

    /**
     * Whether or not to trace the given processor definition.
     *
     * @param  definition the processor definition
     * @param  exchange   the exchange
     * @return            <tt>true</tt> to trace, <tt>false</tt> to skip tracing
     */
    @Override
    public boolean shouldTrace(NamedNode definition, Exchange exchange) {
        // special in standby mode we allow using tracer to capture latest tracing data for
        // enriched message history
        boolean history = (enabled || standby) && camelContext.isMessageHistory();
        if (!history && !enabled) {
            return false;
        }

        boolean pattern = true;
        boolean filter = true;

        if (patterns != null) {
            pattern = shouldTracePattern(definition);
        }
        if (predicate != null) {
            filter = shouldTraceFilter(exchange);
        }

        return pattern && filter;
    }

    private boolean shouldTracePattern(NamedNode definition) {
        for (String pattern : patterns) {
            // match either route id, or node id
            String id = definition.getId();
            // use matchPattern method from endpoint helper that has a good matcher we use in Camel
            if (PatternHelper.matchPattern(id, pattern)) {
                return true;
            }
            String routeId = CamelContextHelper.getRouteId(definition);
            if (routeId != null && !Objects.equals(routeId, id)) {
                if (PatternHelper.matchPattern(routeId, pattern)) {
                    return true;
                }
            }
        }
        // not matched the pattern
        return false;
    }

    @Override
    public void traceFirstNode(NamedNode node, Exchange exchange) {
        traceNode(node, exchange, true, false);
    }

    @Override
    public void traceLastNode(NamedNode node, Exchange exchange) {
        traceNode(node, exchange, false, true);
    }

    private void traceNode(NamedNode node, Exchange exchange, boolean first, boolean last) {
        if (!shouldTrace(node, exchange)) {
            return;
        }
        long timestamp = System.currentTimeMillis();
        String toNode = node.getId();
        String toNodeParentId = node.getParentId();
        String toNodeShortName = node.getShortName();
        String toNodeLabel = StringHelper.limitLength(node.getLabel(), 50);
        String exchangeId = exchange.getExchangeId();
        String correlationExchangeId = exchange.getProperty(ExchangePropertyKey.CORRELATION_ID, String.class);
        String breadcrumbId = exchange.getIn().getHeader(Exchange.BREADCRUMB_ID, String.class);
        int level = node.getLevel();
        String fromRouteId = exchange.getFromRouteId();
        String source = LoggerHelper.getLineNumberLoggerName(node);
        JsonObject data = MessageHelper.dumpAsJSonObject(exchange.getIn(), isIncludeExchangeProperties(),
                isIncludeExchangeVariables(), true, true, isBodyIncludeStreams(), isBodyIncludeFiles(), getBodyMaxChars());
        DefaultBacklogTracerEventMessage event = new DefaultBacklogTracerEventMessage(
                camelContext, first, last, incrementTraceCounter(), timestamp, source, fromRouteId, fromRouteId, toNode,
                toNodeParentId, null, null, toNodeShortName, toNodeLabel, level,
                exchangeId, correlationExchangeId, breadcrumbId, false, false, data);
        if ((first || last) && fromRouteId != null) {
            Route route = camelContext.getRoute(fromRouteId);
            if (route != null && route.getConsumer() != null) {
                Endpoint ep = route.getConsumer().getEndpoint();
                String endpointUri = ep.getEndpointUri();
                event.setEndpointUri(endpointUri);
                event.setRemoteEndpoint(ep.isRemote());
                if ((endpointUri != null && endpointUri.startsWith("stub:"))
                        || "StubEndpoint".equals(ep.getClass().getSimpleName())) {
                    event.setStubEndpoint(true);
                }
            }
        }
        // synthetic events are snapshots, mark done immediately so elapsed doesn't keep growing
        event.doneProcessing();
        traceEvent(event);
    }

    @Override
    public void traceEvent(BacklogTracerEventMessage event) {
        // special in standby mode we allow using tracer to capture latest tracing data for
        // enriched message history
        boolean history = (enabled || standby) && camelContext.isMessageHistory();
        if (!history && !enabled) {
            return;
        }

        // handle capturing events for last full completed exchange (aka replay)
        if (camelContext.isMessageHistory()) {
            // synchronized to prevent race conditions when multiple threads (e.g. timer thread
            // and Kafka consumer threads) call traceEvent concurrently — the peek/offer/addAll/clear
            // sequence must be atomic to avoid dropping events or corrupting the queue state
            synchronized (historyLock) {
                var head = provisionalHistoryQueue.peek();
                String bid = null;
                String tid = null;
                if (head != null) {
                    bid = head.getBreadcrumbId();
                    tid = head.getExchangeId();
                }
                // correlate by breadcrumb ID when available (links exchanges across broker boundaries)
                // fallback to exchange ID / correlation ID matching when breadcrumb is not set
                boolean match;
                if (bid != null && event.getBreadcrumbId() != null) {
                    match = bid.equals(event.getBreadcrumbId());
                } else {
                    match = tid == null || tid.equals(event.getExchangeId())
                            || tid.equals(event.getCorrelationExchangeId());
                }
                // check if this event continues a previously completed breadcrumb flow
                // (e.g. a downstream route connected via Kafka/SEDA that starts after the originating route finished)
                boolean appendMode = false;
                if (head == null && event.getBreadcrumbId() != null
                        && event.getBreadcrumbId().equals(lastCompletedBreadcrumbId)) {
                    appendMode = true;
                } else if (head != null && event.getBreadcrumbId() != null
                        && event.getBreadcrumbId().equals(lastCompletedBreadcrumbId)
                        && head.getBreadcrumbId() != null
                        && head.getBreadcrumbId().equals(lastCompletedBreadcrumbId)) {
                    appendMode = true;
                }
                if (match || appendMode) {
                    boolean added = provisionalHistoryQueue.offer(event);
                    boolean original
                            = head != null && event.getRouteId() != null && event.getRouteId().equals(head.getRouteId());
                    if (event.isLast() && original) {
                        if (appendMode) {
                            // downstream route finished: merge into existing complete history
                            completeHistoryQueue.addAll(provisionalHistoryQueue);
                            if (!added) {
                                completeHistoryQueue.add(event);
                            }
                        } else {
                            // originating route finished: replace complete history
                            completeHistoryQueue.clear();
                            completeHistoryQueue.addAll(provisionalHistoryQueue);
                            if (!added) {
                                completeHistoryQueue.add(event);
                            }
                            lastCompletedBreadcrumbId = event.getBreadcrumbId();
                        }
                        provisionalHistoryQueue.clear();
                    }
                } else if (lastCompletedBreadcrumbId != null && event.getBreadcrumbId() != null
                        && event.getBreadcrumbId().equals(lastCompletedBreadcrumbId)) {
                    // late-arriving event from a downstream route (e.g. second branch of a multicast via Kafka/SEDA)
                    // that arrived after the provisional queue was claimed by a new exchange
                    completeHistoryQueue.add(event);
                }
            }
        }

        if (!enabled) {
            return;
        }

        // pre-drain to make space
        drain(false);

        boolean added = false;
        while (!added) {
            try {
                added = queue.add(event);
            } catch (IllegalStateException e) {
                drain(true);
            }
        }
    }

    private void drain(boolean force) {
        if (force) {
            queue.poll();
        } else {
            int drain = queue.size() - backlogSize + 1;
            if (drain > 0) {
                for (int i = 0; i < drain; i++) {
                    queue.poll();
                }
            }
        }
    }

    private void drainActivity() {
        int drain = activityQueue.size() - activitySize + 1;
        if (drain > 0) {
            for (int i = 0; i < drain; i++) {
                activityQueue.poll();
            }
        }
    }

    private boolean shouldTraceFilter(Exchange exchange) {
        return predicate.matches(exchange);
    }

    @Override
    public boolean isActivityEnabled() {
        return activityEnabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isStandby() {
        return standby;
    }

    @Override
    public void setStandby(boolean standby) {
        this.standby = standby;
    }

    @Override
    public int getBacklogSize() {
        return backlogSize;
    }

    @Override
    public void setBacklogSize(int backlogSize) {
        if (backlogSize <= 0) {
            throw new IllegalArgumentException("The backlog size must be a positive number, was: " + backlogSize);
        }
        if (backlogSize > MAX_BACKLOG_SIZE) {
            throw new IllegalArgumentException(
                    "The backlog size cannot be greater than the max size of " + MAX_BACKLOG_SIZE + ", was: " + backlogSize);
        }
        this.backlogSize = backlogSize;
    }

    @Override
    public int getActivitySize() {
        return activitySize;
    }

    @Override
    public void setActivitySize(int activitySize) {
        if (activitySize <= 0) {
            throw new IllegalArgumentException("The activity size must be a positive number, was: " + activitySize);
        }
        if (activitySize > MAX_BACKLOG_SIZE) {
            throw new IllegalArgumentException(
                    "The activity size cannot be greater than the max size of " + MAX_BACKLOG_SIZE + ", was: " + activitySize);
        }
        this.activitySize = activitySize;
    }

    @Override
    public boolean isRemoveOnDump() {
        return removeOnDump;
    }

    @Override
    public void setRemoveOnDump(boolean removeOnDump) {
        this.removeOnDump = removeOnDump;
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

    @Override
    public boolean isIncludeException() {
        return includeException;
    }

    @Override
    public void setIncludeException(boolean includeException) {
        this.includeException = includeException;
    }

    @Override
    public boolean isTraceRests() {
        return traceRests;
    }

    public void setTraceRests(boolean traceRests) {
        this.traceRests = traceRests;
    }

    public boolean isTraceTemplates() {
        return traceTemplates;
    }

    public void setTraceTemplates(boolean traceTemplates) {
        this.traceTemplates = traceTemplates;
    }

    @Override
    public String getTracePattern() {
        return tracePattern;
    }

    @Override
    public void setTracePattern(String tracePattern) {
        this.tracePattern = tracePattern;
        if (tracePattern != null) {
            // the pattern can have multiple nodes separated by comma
            this.patterns = tracePattern.split(",");
        } else {
            this.patterns = null;
        }
    }

    @Override
    public String getTraceFilter() {
        return traceFilter;
    }

    @Override
    public void setTraceFilter(String filter) {
        this.traceFilter = filter;
        if (filter != null) {
            // assume simple language
            String name = StringHelper.before(filter, ":");
            if (name != null) {
                predicate = camelContext.resolveLanguage(name).createPredicate(filter);
            } else {
                // use simple language by default
                predicate = simple.createPredicate(filter);
            }
        }
    }

    @Override
    public long getTraceCounter() {
        return traceCounter.get();
    }

    @Override
    public long getQueueSize() {
        return queue.size();
    }

    @Override
    public void resetTraceCounter() {
        traceCounter.set(0);
    }

    @Override
    public Collection<BacklogTracerEventMessage> getAllTracedMessages() {
        return Collections.unmodifiableCollection(queue);
    }

    public Collection<BacklogTracerEventMessage> getLatestMessageHistory() {
        return Collections.unmodifiableCollection(completeHistoryQueue);
    }

    @Override
    public Collection<BacklogTracerActivityMessage> getActivity() {
        return Collections.unmodifiableCollection(activityQueue);
    }

    @Override
    public List<BacklogTracerActivityMessage> dumpActivity() {
        List<BacklogTracerActivityMessage> answer = new ArrayList<>(activityQueue);
        if (isRemoveOnDump()) {
            activityQueue.clear();
        }
        return answer;
    }

    @Override
    public String dumpActivityAsJSon() {
        List<BacklogTracerActivityMessage> events = dumpActivity();

        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        root.put("activity", arr);
        for (BacklogTracerActivityMessage event : events) {
            JsonObject jo = new JsonObject();
            jo.put("uid", event.getUid());
            jo.put("exchangeId", event.getExchangeId());
            if (event.getRouteId() != null) {
                jo.put("routeId", event.getRouteId());
            }
            if (event.getFromEndpointUri() != null) {
                jo.put("fromEndpointUri", event.getFromEndpointUri());
            }
            if (event.getTimestamp() > 0) {
                jo.put("timestamp", event.getTimestamp());
            }
            jo.put("elapsed", event.getElapsed());
            jo.put("failed", event.isFailed());
            if (event.getExceptionMessage() != null) {
                jo.put("exception", event.getExceptionMessage());
            }
            List<BacklogTracerActivityMessage.EndpointSend> sends = event.getEndpointSends();
            if (sends != null && !sends.isEmpty()) {
                JsonArray sa = new JsonArray();
                for (BacklogTracerActivityMessage.EndpointSend send : sends) {
                    JsonObject so = new JsonObject();
                    so.put("endpointUri", send.getEndpointUri());
                    so.put("remoteEndpoint", send.isRemoteEndpoint());
                    so.put("elapsed", send.getElapsed());
                    sa.add(so);
                }
                jo.put("endpointSends", sa);
            }
            arr.add(jo);
        }
        return root.toJson();
    }

    public List<BacklogTracerEventMessage> dumpTracedMessages(String nodeId) {
        List<BacklogTracerEventMessage> answer = new ArrayList<>();
        if (nodeId != null) {
            for (BacklogTracerEventMessage message : queue) {
                if (nodeId.equals(message.getToNode()) || nodeId.equals(message.getRouteId())) {
                    answer.add(message);
                }
            }
        }

        if (isRemoveOnDump()) {
            queue.removeAll(answer);
        }

        return answer;
    }

    @Override
    public String dumpTracedMessagesAsXml(String nodeId) {
        List<BacklogTracerEventMessage> events = dumpTracedMessages(nodeId);
        return wrapAroundRootTag(events);
    }

    @Override
    public String dumpLatestMessageHistoryAsXml() {
        List<BacklogTracerEventMessage> events = dumpLatestMessageHistory();
        return wrapAroundRootTag(events);
    }

    @Override
    public String dumpTracedMessagesAsJSon(String nodeId) {
        List<BacklogTracerEventMessage> events = dumpTracedMessages(nodeId);

        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        root.put("traces", arr);
        for (BacklogTracerEventMessage event : events) {
            arr.add(event.asJSon());
        }
        return Jsoner.prettyPrint(root.toJson());
    }

    @Override
    public String dumpLatestMessageHistoryAsJSon() {
        List<BacklogTracerEventMessage> events = dumpLatestMessageHistory();

        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        root.put("traces", arr);
        for (BacklogTracerEventMessage event : events) {
            arr.add(event.asJSon());
        }
        return Jsoner.prettyPrint(root.toJson());
    }

    @Override
    public List<BacklogTracerEventMessage> dumpAllTracedMessages() {
        List<BacklogTracerEventMessage> answer = new ArrayList<>(queue);
        if (isRemoveOnDump()) {
            queue.clear();
        }
        return answer;
    }

    @Override
    public List<BacklogTracerEventMessage> dumpLatestMessageHistory() {
        List<BacklogTracerEventMessage> answer = new ArrayList<>(completeHistoryQueue);
        if (isRemoveOnDump()) {
            completeHistoryQueue.clear();
        }
        return answer;
    }

    @Override
    public String dumpAllTracedMessagesAsXml() {
        List<BacklogTracerEventMessage> events = dumpAllTracedMessages();
        return wrapAroundRootTag(events);
    }

    private static String wrapAroundRootTag(List<BacklogTracerEventMessage> events) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("<").append(BacklogTracerEventMessage.ROOT_TAG).append("s>");
        for (BacklogTracerEventMessage event : events) {
            sb.append("\n").append(event.toXml(2));
        }
        sb.append("\n</").append(BacklogTracerEventMessage.ROOT_TAG).append("s>");
        return sb.toString();
    }

    @Override
    public String dumpAllTracedMessagesAsJSon() {
        List<BacklogTracerEventMessage> events = dumpAllTracedMessages();

        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        root.put("traces", arr);
        for (BacklogTracerEventMessage event : events) {
            arr.add(event.asJSon());
        }
        return Jsoner.prettyPrint(root.toJson());
    }

    @Override
    public void clear() {
        queue.clear();
        completeHistoryQueue.clear();
        provisionalHistoryQueue.clear();
        activityQueue.clear();
        inflightActivity.clear();
    }

    @Override
    public long incrementTraceCounter() {
        return traceCounter.incrementAndGet();
    }

    public void setActivityEnabled(boolean activityEnabled) {
        this.activityEnabled = activityEnabled;
    }

    @Override
    protected void doStart() throws Exception {
        camelContext.getManagementStrategy().addEventNotifier(activityEventNotifier);
        ServiceHelper.startService(activityEventNotifier);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(activityEventNotifier);
        camelContext.getManagementStrategy().removeEventNotifier(activityEventNotifier);
        inflightActivity.clear();
        clear();
    }

    private void onExchangeCreated(CamelEvent.ExchangeCreatedEvent event) {
        evictStaleInflight();

        Exchange exchange = event.getExchange();
        String exchangeId = exchange.getExchangeId();
        long timestamp = event.getTimestamp() > 0 ? event.getTimestamp() : System.currentTimeMillis();

        DefaultBacklogTracerActivityMessage activity = new DefaultBacklogTracerActivityMessage(
                incrementTraceCounter(), timestamp, exchangeId);
        inflightActivity.put(exchangeId, activity);
    }

    private void onExchangeSent(CamelEvent.ExchangeSentEvent event) {
        Endpoint endpoint = event.getEndpoint();
        if (!endpoint.isRemote()) {
            return;
        }

        String exchangeId = event.getExchange().getExchangeId();
        DefaultBacklogTracerActivityMessage activity = inflightActivity.get(exchangeId);
        if (activity != null) {
            activity.addEndpointSend(endpoint.getEndpointUri(), endpoint.isRemote(), event.getTimeTaken());
        }
    }

    private void onExchangeCompleted(CamelEvent.ExchangeCompletedEvent event) {
        Exchange exchange = event.getExchange();
        String exchangeId = exchange.getExchangeId();
        DefaultBacklogTracerActivityMessage activity = inflightActivity.remove(exchangeId);
        if (activity != null) {
            String routeId = exchange.getFromRouteId();
            String fromEndpointUri = resolveFromEndpointUri(routeId);
            activity.complete(routeId, fromEndpointUri, exchange.getClock().elapsed(), false, null);
            drainActivity();
            activityQueue.offer(activity);
        }
    }

    private void onExchangeFailed(CamelEvent.ExchangeFailedEvent event) {
        Exchange exchange = event.getExchange();
        String exchangeId = exchange.getExchangeId();
        DefaultBacklogTracerActivityMessage activity = inflightActivity.remove(exchangeId);
        if (activity != null) {
            String exMessage = null;
            Exception cause = exchange.getException();
            if (cause != null) {
                exMessage = cause.getMessage();
            }
            String routeId = exchange.getFromRouteId();
            String fromEndpointUri = resolveFromEndpointUri(routeId);
            activity.complete(routeId, fromEndpointUri, exchange.getClock().elapsed(), true, exMessage);
            drainActivity();
            activityQueue.offer(activity);
        }
    }

    private String resolveFromEndpointUri(String routeId) {
        if (routeId != null) {
            Route route = camelContext.getRoute(routeId);
            if (route != null && route.getConsumer() != null) {
                return route.getConsumer().getEndpoint().getEndpointUri();
            }
        }
        return null;
    }

    private void evictStaleInflight() {
        if (inflightActivity.isEmpty()) {
            return;
        }
        long cutoff = System.currentTimeMillis() - INFLIGHT_EVICTION_MILLIS;
        inflightActivity.entrySet().removeIf(e -> e.getValue().getTimestamp() < cutoff);
    }

    /**
     * Inner EventNotifier that captures exchange lifecycle events for activity monitoring.
     */
    private class ActivityEventNotifier extends SimpleEventNotifierSupport implements NonManagedService {

        ActivityEventNotifier() {
            // enable the exchange events we need for activity tracking
            setIgnoreExchangeEvents(false);
            setIgnoreExchangeCreatedEvent(false);
            setIgnoreExchangeCompletedEvent(false);
            setIgnoreExchangeFailedEvents(false);
            setIgnoreExchangeSentEvents(false);
            // ignore all non-exchange events
            setIgnoreCamelContextInitEvents(true);
            setIgnoreCamelContextEvents(true);
            setIgnoreRouteEvents(true);
            setIgnoreServiceEvents(true);
            setIgnoreStepEvents(true);
        }

        @Override
        public boolean isDisabled() {
            return !activityEnabled || (!enabled && !standby);
        }

        @Override
        public void notify(CamelEvent event) throws Exception {
            if (event instanceof CamelEvent.ExchangeCreatedEvent ece) {
                onExchangeCreated(ece);
            } else if (event instanceof CamelEvent.ExchangeSentEvent ese) {
                onExchangeSent(ese);
            } else if (event instanceof CamelEvent.ExchangeCompletedEvent ece) {
                onExchangeCompleted(ece);
            } else if (event instanceof CamelEvent.ExchangeFailedEvent efe) {
                onExchangeFailed(efe);
            }
        }
    }
}
