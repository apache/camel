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
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Predicate;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.apache.camel.spi.Language;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.PatternHelper;
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
public final class BacklogTracer extends ServiceSupport implements org.apache.camel.spi.BacklogTracer {

    // lets limit the tracer to 10 thousand messages in total
    public static final int MAX_BACKLOG_SIZE = 10 * 1000;
    private final CamelContext camelContext;
    private final Language simple;
    private boolean enabled;
    private boolean standby;
    private final AtomicLong traceCounter = new AtomicLong();
    // use a queue with an upper limit to avoid storing too many messages
    private final Queue<BacklogTracerEventMessage> queue = new LinkedBlockingQueue<>(MAX_BACKLOG_SIZE);
    // how many of the last messages to keep in the backlog at total
    private int backlogSize = 1000;
    private boolean removeOnDump = true;
    private int bodyMaxChars = 128 * 1024;
    private boolean bodyIncludeStreams;
    private boolean bodyIncludeFiles = true;
    private boolean includeExchangeProperties = true;
    private boolean includeExchangeVariables = true;
    private boolean includeException = true;
    private boolean traceRests;
    private boolean traceTemplates;
    // a pattern to filter tracing nodes
    private String tracePattern;
    private String[] patterns;
    private String traceFilter;
    private Predicate predicate;

    private BacklogTracer(CamelContext camelContext) {
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
    public boolean shouldTrace(NamedNode definition, Exchange exchange) {
        if (!enabled) {
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

    public void traceEvent(DefaultBacklogTracerEventMessage event) {
        if (!enabled) {
            return;
        }

        // ensure there is space on the queue by polling until at least single slot is free
        int drain = queue.size() - backlogSize + 1;
        if (drain > 0) {
            for (int i = 0; i < drain; i++) {
                queue.poll();
            }
        }

        queue.add(event);
    }

    private boolean shouldTraceFilter(Exchange exchange) {
        return predicate.matches(exchange);
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

    public List<BacklogTracerEventMessage> dumpTracedMessages(String nodeId) {
        List<BacklogTracerEventMessage> answer = new ArrayList<>();
        if (nodeId != null) {
            for (BacklogTracerEventMessage message : queue) {
                if (nodeId.equals(message.getToNode()) || nodeId.equals(message.getRouteId())) {
                    answer.add(message);
                }
            }
        }

        if (removeOnDump) {
            queue.removeAll(answer);
        }

        return answer;
    }

    @Override
    public String dumpTracedMessagesAsXml(String nodeId) {
        List<BacklogTracerEventMessage> events = dumpTracedMessages(nodeId);

        StringBuilder sb = new StringBuilder();
        sb.append("<").append(BacklogTracerEventMessage.ROOT_TAG).append("s>");
        for (BacklogTracerEventMessage event : events) {
            sb.append("\n").append(event.toXml(2));
        }
        sb.append("\n</").append(BacklogTracerEventMessage.ROOT_TAG).append("s>");
        return sb.toString();
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
    public List<BacklogTracerEventMessage> dumpAllTracedMessages() {
        List<BacklogTracerEventMessage> answer = new ArrayList<>(queue);
        if (isRemoveOnDump()) {
            queue.clear();
        }
        return answer;
    }

    @Override
    public String dumpAllTracedMessagesAsXml() {
        List<BacklogTracerEventMessage> events = dumpAllTracedMessages();

        StringBuilder sb = new StringBuilder();
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
    }

    public long incrementTraceCounter() {
        return traceCounter.incrementAndGet();
    }

    @Override
    protected void doStop() throws Exception {
        queue.clear();
    }

}
