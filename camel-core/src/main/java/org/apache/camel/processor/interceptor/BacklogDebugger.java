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
package org.apache.camel.processor.interceptor;

import java.util.Date;
import java.util.EventObject;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.api.management.mbean.BacklogTracerEventMessage;
import org.apache.camel.impl.BreakpointSupport;
import org.apache.camel.impl.DefaultDebugger;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.spi.Condition;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.CamelLogger;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.apache.camel.spi.Debugger} that has easy debugging functionality which
 * can be used from JMX with {@link org.apache.camel.api.management.mbean.ManagedBacklogDebuggerMBean}.
 * <p/>
 * This implementation allows to set breakpoints (with or without a condition) and inspect the {@link Exchange}
 * dumped in XML in {@link BacklogTracerEventMessage} format. There is operations to resume suspended breakpoints
 * to continue routing the {@link Exchange}. There is also step functionality so you can single step a given
 * {@link Exchange}.
 * <p/>
 * This implementation will only break the first {@link Exchange} that arrives to a breakpoint. If Camel routes using
 * concurrency then sub-sequent {@link Exchange} will continue to be routed, if there breakpoint already holds a
 * suspended {@link Exchange}.
 */
public class BacklogDebugger extends ServiceSupport implements InterceptStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(BacklogDebugger.class);

    private long fallbackTimeout = 300;
    private final CamelContext camelContext;
    private LoggingLevel loggingLevel = LoggingLevel.INFO;
    private final CamelLogger logger = new CamelLogger(LOG, loggingLevel);
    private final AtomicBoolean enabled = new AtomicBoolean();
    private final AtomicLong debugCounter = new AtomicLong(0);
    private final Debugger debugger;
    private final ConcurrentMap<String, NodeBreakpoint> breakpoints = new ConcurrentHashMap<String, NodeBreakpoint>();
    private final ConcurrentMap<String, SuspendedExchange> suspendedBreakpoints = new ConcurrentHashMap<String, SuspendedExchange>();
    private final ConcurrentMap<String, BacklogTracerEventMessage> suspendedBreakpointMessages = new ConcurrentHashMap<String, BacklogTracerEventMessage>();
    private volatile String singleStepExchangeId;
    private int bodyMaxChars = 128 * 1024;
    private boolean bodyIncludeStreams;
    private boolean bodyIncludeFiles = true;

    /**
     * A suspend {@link Exchange} at a breakpoint.
     */
    private static final class SuspendedExchange {
        private final Exchange exchange;
        private final CountDownLatch latch;

        /**
         * @param exchange the suspend exchange
         * @param latch    the latch to use to continue routing the exchange
         */
        private SuspendedExchange(Exchange exchange, CountDownLatch latch) {
            this.exchange = exchange;
            this.latch = latch;
        }

        public Exchange getExchange() {
            return exchange;
        }

        public CountDownLatch getLatch() {
            return latch;
        }
    }

    public BacklogDebugger(CamelContext camelContext) {
        this.camelContext = camelContext;
        DefaultDebugger debugger = new DefaultDebugger(camelContext);
        debugger.setUseTracer(false);
        this.debugger = debugger;
    }

    @Override
    @Deprecated
    public Processor wrapProcessorInInterceptors(CamelContext context, ProcessorDefinition<?> definition, Processor target, Processor nextTarget) throws Exception {
        throw new UnsupportedOperationException("Deprecated");
    }

    /**
     * A helper method to return the BacklogDebugger instance if one is enabled
     *
     * @return the backlog debugger or null if none can be found
     */
    public static BacklogDebugger getBacklogDebugger(CamelContext context) {
        List<InterceptStrategy> list = context.getInterceptStrategies();
        for (InterceptStrategy interceptStrategy : list) {
            if (interceptStrategy instanceof BacklogDebugger) {
                return (BacklogDebugger) interceptStrategy;
            }
        }
        return null;
    }

    public Debugger getDebugger() {
        return debugger;
    }

    public String getLoggingLevel() {
        return loggingLevel.name();
    }

    public void setLoggingLevel(String level) {
        loggingLevel = LoggingLevel.valueOf(level);
        logger.setLevel(loggingLevel);
    }

    public void enableDebugger() {
        logger.log("Enabling debugger");
        try {
            ServiceHelper.startService(debugger);
            enabled.set(true);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    public void disableDebugger() {
        logger.log("Disabling debugger");
        try {
            enabled.set(false);
            ServiceHelper.stopService(debugger);
        } catch (Exception e) {
            // ignore
        }
        clearBreakpoints();
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public boolean hasBreakpoint(String nodeId) {
        return breakpoints.containsKey(nodeId);
    }

    public boolean isSingleStepMode() {
        return singleStepExchangeId != null;
    }

    public void addBreakpoint(String nodeId) {
        NodeBreakpoint breakpoint = breakpoints.get(nodeId);
        if (breakpoint == null) {
            logger.log("Adding breakpoint " + nodeId);
            breakpoint = new NodeBreakpoint(nodeId, null);
            breakpoints.put(nodeId, breakpoint);
            debugger.addBreakpoint(breakpoint, breakpoint);
        } else {
            breakpoint.setCondition(null);
        }
    }

    public void addConditionalBreakpoint(String nodeId, String language, String predicate) {
        Predicate condition = camelContext.resolveLanguage(language).createPredicate(predicate);
        NodeBreakpoint breakpoint = breakpoints.get(nodeId);
        if (breakpoint == null) {
            logger.log("Adding conditional breakpoint " + nodeId + " [" + predicate + "]");
            breakpoint = new NodeBreakpoint(nodeId, condition);
            breakpoints.put(nodeId, breakpoint);
            debugger.addBreakpoint(breakpoint, breakpoint);
        } else if (breakpoint.getCondition() == null) {
            logger.log("Updating to conditional breakpoint " + nodeId + " [" + predicate + "]");
            debugger.removeBreakpoint(breakpoint);
            breakpoints.put(nodeId, breakpoint);
            debugger.addBreakpoint(breakpoint, breakpoint);
        } else if (breakpoint.getCondition() != null) {
            logger.log("Updating conditional breakpoint " + nodeId + " [" + predicate + "]");
            breakpoint.setCondition(condition);
        }
    }

    public void removeBreakpoint(String nodeId) {
        logger.log("Removing breakpoint " + nodeId);
        // when removing a break point then ensure latches is cleared and counted down so we wont have hanging threads
        suspendedBreakpointMessages.remove(nodeId);
        SuspendedExchange se = suspendedBreakpoints.remove(nodeId);
        NodeBreakpoint breakpoint = breakpoints.remove(nodeId);
        if (breakpoint != null) {
            debugger.removeBreakpoint(breakpoint);
        }
        if (se != null) {
            se.getLatch().countDown();
        }
    }

    public void removeAllBreakpoints() {
        // stop single stepping
        singleStepExchangeId = null;

        for (String nodeId : getSuspendedBreakpointNodeIds()) {
            removeBreakpoint(nodeId);
        }
    }

    public Set<String> getBreakpoints() {
        return new LinkedHashSet<String>(breakpoints.keySet());
    }

    public void resumeBreakpoint(String nodeId) {
        resumeBreakpoint(nodeId, false);
    }

    private void resumeBreakpoint(String nodeId, boolean stepMode) {
        logger.log("Resume breakpoint " + nodeId);

        if (!stepMode) {
            if (singleStepExchangeId != null) {
                debugger.stopSingleStepExchange(singleStepExchangeId);
                singleStepExchangeId = null;
            }
        }

        // remember to remove the dumped message as its no longer in need
        suspendedBreakpointMessages.remove(nodeId);
        SuspendedExchange se = suspendedBreakpoints.remove(nodeId);
        if (se != null) {
            se.getLatch().countDown();
        }
    }

    public void setMessageBodyOnBreakpoint(String nodeId, Object body) {
        SuspendedExchange se = suspendedBreakpoints.get(nodeId);
        if (se != null) {
            boolean remove = body == null;
            if (remove) {
                removeMessageBodyOnBreakpoint(nodeId);
            } else {
                Class<?> oldType;
                if (se.getExchange().hasOut()) {
                    oldType = se.getExchange().getOut().getBody() != null ? se.getExchange().getOut().getBody().getClass() : null;
                } else {
                    oldType = se.getExchange().getIn().getBody() != null ? se.getExchange().getIn().getBody().getClass() : null;
                }
                setMessageBodyOnBreakpoint(nodeId, body, oldType);
            }
        }
    }

    public void setMessageBodyOnBreakpoint(String nodeId, Object body, Class<?> type) {
        SuspendedExchange se = suspendedBreakpoints.get(nodeId);
        if (se != null) {
            boolean remove = body == null;
            if (remove) {
                removeMessageBodyOnBreakpoint(nodeId);
            } else {
                logger.log("Breakpoint at node " + nodeId + " is updating message body on exchangeId: " + se.getExchange().getExchangeId() + " with new body: " + body);
                if (se.getExchange().hasOut()) {
                    // preserve type
                    if (type != null) {
                        se.getExchange().getOut().setBody(body, type);
                    } else {
                        se.getExchange().getOut().setBody(body);
                    }
                } else {
                    if (type != null) {
                        se.getExchange().getIn().setBody(body, type);
                    } else {
                        se.getExchange().getIn().setBody(body);
                    }
                }
            }
        }
    }

    public void removeMessageBodyOnBreakpoint(String nodeId) {
        SuspendedExchange se = suspendedBreakpoints.get(nodeId);
        if (se != null) {
            logger.log("Breakpoint at node " + nodeId + " is removing message body on exchangeId: " + se.getExchange().getExchangeId());
            if (se.getExchange().hasOut()) {
                se.getExchange().getOut().setBody(null);
            } else {
                se.getExchange().getIn().setBody(null);
            }
        }
    }

    public void setMessageHeaderOnBreakpoint(String nodeId, String headerName, Object value) throws NoTypeConversionAvailableException {
        SuspendedExchange se = suspendedBreakpoints.get(nodeId);
        if (se != null) {
            Class<?> oldType;
            if (se.getExchange().hasOut()) {
                oldType = se.getExchange().getOut().getHeader(headerName) != null ? se.getExchange().getOut().getHeader(headerName).getClass() : null;
            } else {
                oldType = se.getExchange().getIn().getHeader(headerName) != null ? se.getExchange().getIn().getHeader(headerName).getClass() : null;
            }
            setMessageHeaderOnBreakpoint(nodeId, headerName, value, oldType);
        }
    }

    public void setMessageHeaderOnBreakpoint(String nodeId, String headerName, Object value, Class<?> type) throws NoTypeConversionAvailableException {
        SuspendedExchange se = suspendedBreakpoints.get(nodeId);
        if (se != null) {
            logger.log("Breakpoint at node " + nodeId + " is updating message header on exchangeId: " + se.getExchange().getExchangeId() + " with header: " + headerName + " and value: " + value);
            if (se.getExchange().hasOut()) {
                if (type != null) {
                    Object convertedValue = se.getExchange().getContext().getTypeConverter().mandatoryConvertTo(type, se.getExchange(), value);
                    se.getExchange().getOut().setHeader(headerName, convertedValue);
                } else {
                    se.getExchange().getOut().setHeader(headerName, value);
                }
            } else {
                if (type != null) {
                    Object convertedValue = se.getExchange().getContext().getTypeConverter().mandatoryConvertTo(type, se.getExchange(), value);
                    se.getExchange().getIn().setHeader(headerName, convertedValue);
                } else {
                    se.getExchange().getIn().setHeader(headerName, value);
                }
            }
        }
    }

    public long getFallbackTimeout() {
        return fallbackTimeout;
    }

    public void setFallbackTimeout(long fallbackTimeout) {
        this.fallbackTimeout = fallbackTimeout;
    }

    public void removeMessageHeaderOnBreakpoint(String nodeId, String headerName) {
        SuspendedExchange se = suspendedBreakpoints.get(nodeId);
        if (se != null) {
            logger.log("Breakpoint at node " + nodeId + " is removing message header on exchangeId: " + se.getExchange().getExchangeId() + " with header: " + headerName);
            if (se.getExchange().hasOut()) {
                se.getExchange().getOut().removeHeader(headerName);
            } else {
                se.getExchange().getIn().removeHeader(headerName);
            }
        }
    }

    public void resumeAll() {
        logger.log("Resume all");
        // stop single stepping
        singleStepExchangeId = null;

        for (String node : getSuspendedBreakpointNodeIds()) {
            // remember to remove the dumped message as its no longer in need
            suspendedBreakpointMessages.remove(node);
            SuspendedExchange se = suspendedBreakpoints.remove(node);
            if (se != null) {
                se.getLatch().countDown();
            }
        }
    }

    public void stepBreakpoint(String nodeId) {
        // if we are already in single step mode, then infer stepping
        if (isSingleStepMode()) {
            logger.log("stepBreakpoint " + nodeId + " is already in single step mode, so stepping instead.");
            step();
        }

        logger.log("Step breakpoint " + nodeId);
        // we want to step current exchange to next
        BacklogTracerEventMessage msg = suspendedBreakpointMessages.get(nodeId);
        NodeBreakpoint breakpoint = breakpoints.get(nodeId);
        if (msg != null && breakpoint != null) {
            singleStepExchangeId = msg.getExchangeId();
            if (debugger.startSingleStepExchange(singleStepExchangeId, new StepBreakpoint())) {
                // now resume
                resumeBreakpoint(nodeId, true);
            }
        }
    }

    public void step() {
        for (String node : getSuspendedBreakpointNodeIds()) {
            // remember to remove the dumped message as its no longer in need
            suspendedBreakpointMessages.remove(node);
            SuspendedExchange se = suspendedBreakpoints.remove(node);
            if (se != null) {
                se.getLatch().countDown();
            }
        }
    }

    public Set<String> getSuspendedBreakpointNodeIds() {
        return new LinkedHashSet<String>(suspendedBreakpoints.keySet());
    }

    /**
     * Gets the exchanged suspended at the given breakpoint id or null if there is none at that id.
     *
     * @param id - node id for the breakpoint
     * @return The suspended exchange or null if there isn't one suspended at the given breakpoint.
     */
    public Exchange getSuspendedExchange(String id) {
        SuspendedExchange suspendedExchange = suspendedBreakpoints.get(id);
        return suspendedExchange != null ? suspendedExchange.getExchange() : null;
    }

    public void disableBreakpoint(String nodeId) {
        logger.log("Disable breakpoint " + nodeId);
        NodeBreakpoint breakpoint = breakpoints.get(nodeId);
        if (breakpoint != null) {
            breakpoint.suspend();
        }
    }

    public void enableBreakpoint(String nodeId) {
        logger.log("Enable breakpoint " + nodeId);
        NodeBreakpoint breakpoint = breakpoints.get(nodeId);
        if (breakpoint != null) {
            breakpoint.activate();
        }
    }

    public int getBodyMaxChars() {
        return bodyMaxChars;
    }

    public void setBodyMaxChars(int bodyMaxChars) {
        this.bodyMaxChars = bodyMaxChars;
    }

    public boolean isBodyIncludeStreams() {
        return bodyIncludeStreams;
    }

    public void setBodyIncludeStreams(boolean bodyIncludeStreams) {
        this.bodyIncludeStreams = bodyIncludeStreams;
    }

    public boolean isBodyIncludeFiles() {
        return bodyIncludeFiles;
    }

    public void setBodyIncludeFiles(boolean bodyIncludeFiles) {
        this.bodyIncludeFiles = bodyIncludeFiles;
    }

    public String dumpTracedMessagesAsXml(String nodeId) {
        logger.log("Dump trace message from breakpoint " + nodeId);
        BacklogTracerEventMessage msg = suspendedBreakpointMessages.get(nodeId);
        if (msg != null) {
            return msg.toXml(0);
        } else {
            return null;
        }
    }

    public long getDebugCounter() {
        return debugCounter.get();
    }

    public void resetDebugCounter() {
        logger.log("Reset debug counter");
        debugCounter.set(0);
    }

    public boolean beforeProcess(Exchange exchange, Processor processor, ProcessorDefinition<?> definition) {
        return debugger.beforeProcess(exchange, processor, definition);
    }

    public boolean afterProcess(Exchange exchange, Processor processor, ProcessorDefinition<?> definition, long timeTaken) {
        // noop
        return false;
    }

    protected void doStart() throws Exception {
        // noop
    }

    protected void doStop() throws Exception {
        if (enabled.get()) {
            disableDebugger();
        }
        clearBreakpoints();
    }

    private void clearBreakpoints() {
        // make sure to clear state and latches is counted down so we wont have hanging threads
        breakpoints.clear();
        for (SuspendedExchange se : suspendedBreakpoints.values()) {
            se.getLatch().countDown();
        }
        suspendedBreakpoints.clear();
        suspendedBreakpointMessages.clear();
    }

    /**
     * Represents a {@link org.apache.camel.spi.Breakpoint} that has a {@link Condition} on a specific node id.
     */
    private final class NodeBreakpoint extends BreakpointSupport implements Condition {

        private final String nodeId;
        private Predicate condition;

        private NodeBreakpoint(String nodeId, Predicate condition) {
            this.nodeId = nodeId;
            this.condition = condition;
        }

        public Predicate getCondition() {
            return condition;
        }

        public void setCondition(Predicate predicate) {
            this.condition = predicate;
        }

        @Override
        public void beforeProcess(Exchange exchange, Processor processor, ProcessorDefinition<?> definition) {
            // store a copy of the message so we can see that from the debugger
            Date timestamp = new Date();
            String toNode = definition.getId();
            String routeId = ProcessorDefinitionHelper.getRouteId(definition);
            String exchangeId = exchange.getExchangeId();
            String messageAsXml = MessageHelper.dumpAsXml(exchange.getIn(), true, 2, isBodyIncludeStreams(), isBodyIncludeFiles(), getBodyMaxChars());
            long uid = debugCounter.incrementAndGet();

            BacklogTracerEventMessage msg = new DefaultBacklogTracerEventMessage(uid, timestamp, routeId, toNode, exchangeId, messageAsXml);
            suspendedBreakpointMessages.put(nodeId, msg);

            // suspend at this breakpoint
            final SuspendedExchange se = suspendedBreakpoints.get(nodeId);
            if (se != null) {
                // now wait until we should continue
                logger.log("NodeBreakpoint at node " + toNode + " is waiting to continue for exchangeId: " + exchangeId);
                try {
                    boolean hit = se.getLatch().await(fallbackTimeout, TimeUnit.SECONDS);
                    if (!hit) {
                        logger.log("NodeBreakpoint at node " + toNode + " timed out and is continued exchangeId: " + exchangeId, LoggingLevel.WARN);
                    } else {
                        logger.log("NodeBreakpoint at node " + toNode + " is continued exchangeId: " + exchangeId);
                    }
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }

        @Override
        public boolean matchProcess(Exchange exchange, Processor processor, ProcessorDefinition<?> definition) {
            // must match node
            if (!nodeId.equals(definition.getId())) {
                return false;
            }

            // if condition then must match
            if (condition != null && !condition.matches(exchange)) {
                return false;
            }

            // we only want to break one exchange at a time, so if there is already a suspended breakpoint then do not match
            SuspendedExchange se = new SuspendedExchange(exchange, new CountDownLatch(1));
            boolean existing = suspendedBreakpoints.putIfAbsent(nodeId, se) != null;
            return !existing;
        }

        @Override
        public boolean matchEvent(Exchange exchange, EventObject event) {
            return false;
        }
    }

    /**
     * Represents a {@link org.apache.camel.spi.Breakpoint} that is used during single step mode.
     */
    private final class StepBreakpoint extends BreakpointSupport implements Condition {

        @Override
        public void beforeProcess(Exchange exchange, Processor processor, ProcessorDefinition<?> definition) {
            // store a copy of the message so we can see that from the debugger
            Date timestamp = new Date();
            String toNode = definition.getId();
            String routeId = ProcessorDefinitionHelper.getRouteId(definition);
            String exchangeId = exchange.getExchangeId();
            String messageAsXml = MessageHelper.dumpAsXml(exchange.getIn(), true, 2, isBodyIncludeStreams(), isBodyIncludeFiles(), getBodyMaxChars());
            long uid = debugCounter.incrementAndGet();

            BacklogTracerEventMessage msg = new DefaultBacklogTracerEventMessage(uid, timestamp, routeId, toNode, exchangeId, messageAsXml);
            suspendedBreakpointMessages.put(toNode, msg);

            // suspend at this breakpoint
            SuspendedExchange se = new SuspendedExchange(exchange, new CountDownLatch(1));
            suspendedBreakpoints.put(toNode, se);

            // now wait until we should continue
            logger.log("StepBreakpoint at node " + toNode + " is waiting to continue for exchangeId: " + exchange.getExchangeId());
            try {
                boolean hit = se.getLatch().await(fallbackTimeout, TimeUnit.SECONDS);
                if (!hit) {
                    logger.log("StepBreakpoint at node " + toNode + " timed out and is continued exchangeId: " + exchange.getExchangeId(), LoggingLevel.WARN);
                } else {
                    logger.log("StepBreakpoint at node " + toNode + " is continued exchangeId: " + exchange.getExchangeId());
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }

        @Override
        public boolean matchProcess(Exchange exchange, Processor processor, ProcessorDefinition<?> definition) {
            return true;
        }

        @Override
        public boolean matchEvent(Exchange exchange, EventObject event) {
            return event instanceof ExchangeCompletedEvent;
        }

        @Override
        public void onEvent(Exchange exchange, EventObject event, ProcessorDefinition<?> definition) {
            // when the exchange is complete, we need to turn off single step mode if we were debug stepping the exchange
            if (event instanceof ExchangeCompletedEvent) {
                String completedId = ((ExchangeCompletedEvent) event).getExchange().getExchangeId();

                if (singleStepExchangeId != null && singleStepExchangeId.equals(completedId)) {
                    logger.log("ExchangeId: " + completedId + " is completed, so exiting single step mode.");
                    singleStepExchangeId = null;
                }
            }
        }
    }

}
