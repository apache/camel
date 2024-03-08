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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.LoggingLevel;
import org.apache.camel.MessageHistory;
import org.apache.camel.NamedNode;
import org.apache.camel.NamedRoute;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.BacklogDebugger;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeEvent;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.Condition;
import org.apache.camel.spi.Debugger;
import org.apache.camel.support.BreakpointSupport;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultBacklogDebugger extends ServiceSupport implements BacklogDebugger {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultBacklogDebugger.class);

    private long fallbackTimeout = 300;
    private final CamelContext camelContext;
    private LoggingLevel loggingLevel = LoggingLevel.INFO;
    private final CamelLogger logger = new CamelLogger(LOG, loggingLevel);
    private final AtomicBoolean enabled = new AtomicBoolean();
    private final AtomicBoolean standby = new AtomicBoolean();
    private final AtomicLong debugCounter = new AtomicLong();
    private final Debugger debugger;
    private final ConcurrentMap<String, NodeBreakpoint> breakpoints = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SuspendedExchange> suspendedBreakpoints = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, BacklogTracerEventMessage> suspendedBreakpointMessages = new ConcurrentHashMap<>();

    private final AtomicReference<CountDownLatch> suspend = new AtomicReference<>();
    private volatile String singleStepExchangeId;

    private boolean suspendMode;
    private String initialBreakpoints;
    private boolean singleStepIncludeStartEnd;
    private int bodyMaxChars = 128 * 1024;
    private boolean bodyIncludeStreams;
    private boolean bodyIncludeFiles = true;
    private boolean includeExchangeProperties = true;
    private boolean includeExchangeVariables = true;
    private boolean includeException = true;

    /**
     * An suspend {@link Exchange} at a breakpoint.
     */
    private static final class SuspendedExchange {
        private final Exchange exchange;
        private final CountDownLatch latch;

        /**
         * @param exchange the suspended exchange
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

    /**
     * Constructs a {@code BacklogDebugger} with the given parameters.
     *
     * @param camelContext the camel context
     * @param suspendMode  Indicates whether the <i>suspend mode</i> is enabled or not. If {@code true} the message
     *                     processing is immediately suspended until the {@link #attach()} is called.
     */
    private DefaultBacklogDebugger(CamelContext camelContext, boolean suspendMode) {
        this.camelContext = camelContext;
        this.debugger = new DefaultDebugger(camelContext);
        this.suspendMode = suspendMode;
        detach();
    }

    /**
     * Creates a new backlog debugger.
     * <p>
     * In case the environment variable {@link #SUSPEND_MODE_ENV_VAR_NAME} or the system property
     * {@link #SUSPEND_MODE_SYSTEM_PROP_NAME} has been set to {@code true}, the message processing is directly
     * suspended.
     *
     * @param  context Camel context
     * @return         a new backlog debugger
     */
    public static BacklogDebugger createDebugger(CamelContext context) {
        // must enable source location so debugger tooling knows to map breakpoints to source code
        context.setSourceLocationEnabled(true);
        // must enable message history for debugger to capture more details
        context.setMessageHistory(true);

        DefaultBacklogDebugger answer = new DefaultBacklogDebugger(context, resolveSuspendMode());
        answer.setStandby(context.isDebugStandby());
        return answer;
    }

    /**
     * A helper method to return the BacklogDebugger instance if one is enabled
     *
     * @return the backlog debugger or null if none can be found
     */
    public static BacklogDebugger getBacklogDebugger(CamelContext context) {
        return context.hasService(DefaultBacklogDebugger.class);
    }

    @Override
    public String getInitialBreakpoints() {
        return initialBreakpoints;
    }

    @Override
    public void setInitialBreakpoints(String initialBreakpoints) {
        this.initialBreakpoints = initialBreakpoints;
    }

    @Override
    public String getLoggingLevel() {
        return loggingLevel.name();
    }

    @Override
    public void setLoggingLevel(String level) {
        loggingLevel = LoggingLevel.valueOf(level);
        logger.setLevel(loggingLevel);
    }

    @Override
    public void enableDebugger() {
        logger.log("Enabling Camel debugger");
        try {
            ServiceHelper.startService(debugger);
            enabled.set(true);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    @Override
    public void disableDebugger() {
        logger.log("Disabling Camel debugger");
        try {
            enabled.set(false);
            ServiceHelper.stopService(debugger);
        } catch (Exception e) {
            // ignore
        }
        clearBreakpoints();
    }

    @Override
    public boolean isEnabled() {
        return enabled.get();
    }

    @Override
    public boolean isStandby() {
        return standby.get();
    }

    @Override
    public void setStandby(boolean standby) {
        this.standby.set(standby);
    }

    @Override
    public boolean hasBreakpoint(String nodeId) {
        return breakpoints.containsKey(nodeId);
    }

    @Override
    public void setSuspendMode(boolean suspendMode) {
        this.suspendMode = suspendMode;
    }

    @Override
    public boolean isSuspendMode() {
        return suspendMode;
    }

    @Override
    public boolean isSingleStepMode() {
        return singleStepExchangeId != null;
    }

    @Override
    public void attach() {
        if (suspendMode) {
            logger.log("A debugger has been attached");
            resumeMessageProcessing();
        }
    }

    @Override
    public void detach() {
        if (suspendMode) {
            logger.log("Waiting for a debugger to attach");
            suspendMessageProcessing();
        }
    }

    /**
     * Resolves the value of the flag indicating whether the {@code BacklogDebugger} should suspend processing the
     * messages and wait for a debugger to attach or not.
     *
     * @return the value of the environment variable {@link #SUSPEND_MODE_ENV_VAR_NAME} if it has been set, otherwise
     *         the value of the system property {@link #SUSPEND_MODE_SYSTEM_PROP_NAME}, {@code false} by default.
     */
    private static boolean resolveSuspendMode() {
        final String value = System.getenv(SUSPEND_MODE_ENV_VAR_NAME);
        return value == null ? Boolean.getBoolean(SUSPEND_MODE_SYSTEM_PROP_NAME) : Boolean.parseBoolean(value);
    }

    /**
     * Suspend the current thread if the <i>suspend mode</i> is enabled as long as the method {@link #attach()} is not
     * called. Do nothing otherwise.
     */
    private void suspendIfNeeded() {
        final CountDownLatch countDownLatch = suspend.get();
        if (countDownLatch != null) {
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Make Camel suspend processing incoming messages.
     */
    private void suspendMessageProcessing() {
        suspend.compareAndSet(null, new CountDownLatch(1));
    }

    /**
     * Resume the processing of the incoming messages.
     */
    private void resumeMessageProcessing() {
        for (;;) {
            final CountDownLatch countDownLatch = suspend.get();
            if (countDownLatch == null) {
                break;
            } else if (suspend.compareAndSet(countDownLatch, null)) {
                countDownLatch.countDown();
            }
        }
    }

    @Override
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

    @Override
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
        } else {
            logger.log("Updating conditional breakpoint " + nodeId + " [" + predicate + "]");
            breakpoint.setCondition(condition);
        }
    }

    @Override
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

    @Override
    public void removeAllBreakpoints() {
        // stop single stepping
        singleStepExchangeId = null;

        for (String nodeId : getSuspendedBreakpointNodeIds()) {
            removeBreakpoint(nodeId);
        }
    }

    @Override
    public Set<String> getBreakpoints() {
        return new LinkedHashSet<>(breakpoints.keySet());
    }

    @Override
    public void resumeBreakpoint(String nodeId) {
        resumeBreakpoint(nodeId, false);
    }

    @Override
    public void resumeBreakpoint(String nodeId, boolean stepMode) {
        logger.log("Resume breakpoint " + nodeId);

        if (!stepMode && singleStepExchangeId != null) {
            debugger.stopSingleStepExchange(singleStepExchangeId);
            singleStepExchangeId = null;
        }

        // remember to remove the dumped message as its no longer in need
        suspendedBreakpointMessages.remove(nodeId);
        SuspendedExchange se = suspendedBreakpoints.remove(nodeId);
        if (se != null) {
            se.getLatch().countDown();
        }
    }

    @Override
    public void setMessageBodyOnBreakpoint(String nodeId, Object body) {
        SuspendedExchange se = suspendedBreakpoints.get(nodeId);
        if (se != null) {
            boolean remove = body == null;
            if (remove) {
                removeMessageBodyOnBreakpoint(nodeId);
            } else {
                Class<?> oldType = se.getExchange().getMessage().getBody() == null
                        ? null : se.getExchange().getMessage().getBody().getClass();
                setMessageBodyOnBreakpoint(nodeId, body, oldType);
            }
        }
    }

    @Override
    public void setMessageBodyOnBreakpoint(String nodeId, Object body, Class<?> type) {
        SuspendedExchange se = suspendedBreakpoints.get(nodeId);
        if (se != null) {
            boolean remove = body == null;
            if (remove) {
                removeMessageBodyOnBreakpoint(nodeId);
            } else {
                logger.log(String.format("Breakpoint at node %s is updating message body on exchangeId: %s with new body: %s",
                        nodeId, se.getExchange().getExchangeId(), body));
                // preserve type
                if (type == null) {
                    se.getExchange().getMessage().setBody(body);
                } else {
                    se.getExchange().getMessage().setBody(body, type);
                }
                refreshBacklogTracerEventMessage(nodeId, se);
            }
        }
    }

    @Override
    public void removeMessageBodyOnBreakpoint(String nodeId) {
        SuspendedExchange se = suspendedBreakpoints.get(nodeId);
        if (se != null) {
            logger.log(String.format("Breakpoint at node %s is removing message body on exchangeId: %s", nodeId,
                    se.getExchange().getExchangeId()));
            se.getExchange().getMessage().setBody(null);
            refreshBacklogTracerEventMessage(nodeId, se);
        }
    }

    @Override
    public void setMessageHeaderOnBreakpoint(String nodeId, String headerName, Object value)
            throws NoTypeConversionAvailableException {
        SuspendedExchange se = suspendedBreakpoints.get(nodeId);
        if (se != null) {
            Class<?> oldType = se.getExchange().getMessage().getHeader(headerName) == null
                    ? null : se.getExchange().getMessage().getHeader(headerName).getClass();
            setMessageHeaderOnBreakpoint(nodeId, headerName, value, oldType);
        }
    }

    @Override
    public void setMessageHeaderOnBreakpoint(String nodeId, String headerName, Object value, Class<?> type)
            throws NoTypeConversionAvailableException {
        SuspendedExchange se = suspendedBreakpoints.get(nodeId);
        if (se != null) {
            logger.log("Breakpoint at node " + nodeId + " is updating message header on exchangeId: "
                       + se.getExchange().getExchangeId() + " with key: " + headerName + " and value: " + value);
            if (type == null) {
                se.getExchange().getMessage().setHeader(headerName, value);
            } else {
                Object convertedValue
                        = se.getExchange().getContext().getTypeConverter().mandatoryConvertTo(type, se.getExchange(), value);
                se.getExchange().getMessage().setHeader(headerName, convertedValue);
            }
            refreshBacklogTracerEventMessage(nodeId, se);
        }
    }

    @Override
    public void setExchangePropertyOnBreakpoint(String nodeId, String exchangePropertyName, Object value)
            throws NoTypeConversionAvailableException {
        SuspendedExchange se = suspendedBreakpoints.get(nodeId);
        if (se != null) {
            Class<?> oldType = se.getExchange().getMessage().getHeader(exchangePropertyName) == null
                    ? null : se.getExchange().getMessage().getHeader(exchangePropertyName).getClass();
            setExchangePropertyOnBreakpoint(nodeId, exchangePropertyName, value, oldType);
        }
    }

    @Override
    public void setExchangePropertyOnBreakpoint(String nodeId, String exchangePropertyName, Object value, Class<?> type)
            throws NoTypeConversionAvailableException {
        SuspendedExchange se = suspendedBreakpoints.get(nodeId);
        if (se != null) {
            logger.log("Breakpoint at node " + nodeId + " is updating exchange property on exchangeId: "
                       + se.getExchange().getExchangeId() + " with key: " + exchangePropertyName + " and value: " + value);
            if (type == null) {
                se.getExchange().setProperty(exchangePropertyName, value);
            } else {
                Object convertedValue
                        = se.getExchange().getContext().getTypeConverter().mandatoryConvertTo(type, se.getExchange(), value);
                se.getExchange().setProperty(exchangePropertyName, convertedValue);
            }
            refreshBacklogTracerEventMessage(nodeId, se);
        }
    }

    @Override
    public void removeExchangePropertyOnBreakpoint(String nodeId, String exchangePropertyName) {
        SuspendedExchange se = suspendedBreakpoints.get(nodeId);
        if (se != null) {
            logger.log("Breakpoint at node " + nodeId + " is removing exchange property on exchangeId: "
                       + se.getExchange().getExchangeId() + " with key: " + exchangePropertyName);
            se.getExchange().removeProperty(exchangePropertyName);
            refreshBacklogTracerEventMessage(nodeId, se);
        }
    }

    @Override
    public void setExchangeVariableOnBreakpoint(String nodeId, String variableName, Object value)
            throws NoTypeConversionAvailableException {
        SuspendedExchange se = suspendedBreakpoints.get(nodeId);
        if (se != null) {
            Class<?> oldType = se.getExchange().getMessage().getHeader(variableName) == null
                    ? null : se.getExchange().getMessage().getHeader(variableName).getClass();
            setExchangeVariableOnBreakpoint(nodeId, variableName, value, oldType);
        }
    }

    @Override
    public void setExchangeVariableOnBreakpoint(String nodeId, String variableName, Object value, Class<?> type)
            throws NoTypeConversionAvailableException {
        SuspendedExchange se = suspendedBreakpoints.get(nodeId);
        if (se != null) {
            logger.log("Breakpoint at node " + nodeId + " is updating exchange variable on exchangeId: "
                       + se.getExchange().getExchangeId() + " with key: " + variableName + " and value: " + value);
            if (type == null) {
                se.getExchange().setVariable(variableName, value);
            } else {
                Object convertedValue
                        = se.getExchange().getContext().getTypeConverter().mandatoryConvertTo(type, se.getExchange(), value);
                se.getExchange().setVariable(variableName, convertedValue);
            }
            refreshBacklogTracerEventMessage(nodeId, se);
        }
    }

    @Override
    public void removeExchangeVariableOnBreakpoint(String nodeId, String variableName) {
        SuspendedExchange se = suspendedBreakpoints.get(nodeId);
        if (se != null) {
            logger.log("Breakpoint at node " + nodeId + " is removing variable on exchangeId: "
                       + se.getExchange().getExchangeId() + " with key: " + variableName);
            se.getExchange().removeVariable(variableName);
            refreshBacklogTracerEventMessage(nodeId, se);
        }
    }

    @Override
    public long getFallbackTimeout() {
        return fallbackTimeout;
    }

    @Override
    public void setFallbackTimeout(long fallbackTimeout) {
        this.fallbackTimeout = fallbackTimeout;
    }

    @Override
    public void removeMessageHeaderOnBreakpoint(String nodeId, String headerName) {
        SuspendedExchange se = suspendedBreakpoints.get(nodeId);
        if (se != null) {
            logger.log("Breakpoint at node " + nodeId + " is removing message header on exchangeId: "
                       + se.getExchange().getExchangeId() + " with header: " + headerName);
            se.getExchange().getMessage().removeHeader(headerName);
            refreshBacklogTracerEventMessage(nodeId, se);
        }
    }

    @Override
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

    @Override
    public void stepBreakpoint() {
        // if we are already in single step mode, then infer stepping
        if (isSingleStepMode()) {
            logger.log("Step breakpoint is already in single step mode, so stepping instead.");
            step();
        }

        if (suspendedBreakpointMessages.size() != 1) {
            return;
        }

        BacklogTracerEventMessage msg = suspendedBreakpointMessages.values().iterator().next();
        if (msg != null) {
            String nodeId = msg.getToNode();
            NodeBreakpoint breakpoint = breakpoints.get(nodeId);
            if (breakpoint != null) {
                singleStepExchangeId = msg.getExchangeId();
                if (debugger.startSingleStepExchange(singleStepExchangeId, new StepBreakpoint())) {
                    // now resume
                    resumeBreakpoint(nodeId, true);
                }
            }
        }
    }

    @Override
    public void stepBreakpoint(String nodeId) {
        // if we are already in single step mode, then infer stepping
        if (isSingleStepMode()) {
            logger.log("Step breakpoint " + nodeId + " is already in single step mode, so stepping instead.");
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

    @Override
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

    @Override
    public Set<String> getSuspendedBreakpointNodeIds() {
        return new LinkedHashSet<>(suspendedBreakpoints.keySet());
    }

    @Override
    public Exchange getSuspendedExchange(String id) {
        SuspendedExchange suspendedExchange = suspendedBreakpoints.get(id);
        return suspendedExchange == null ? null : suspendedExchange.getExchange();
    }

    @Override
    public BacklogTracerEventMessage getSuspendedBreakpointMessage(String id) {
        return suspendedBreakpointMessages.get(id);
    }

    @Override
    public void disableBreakpoint(String nodeId) {
        logger.log("Disable breakpoint " + nodeId);
        NodeBreakpoint breakpoint = breakpoints.get(nodeId);
        if (breakpoint != null) {
            breakpoint.suspend();
        }
    }

    @Override
    public void enableBreakpoint(String nodeId) {
        logger.log("Enable breakpoint " + nodeId);
        NodeBreakpoint breakpoint = breakpoints.get(nodeId);
        if (breakpoint != null) {
            breakpoint.activate();
        }
    }

    @Override
    public boolean isSingleStepIncludeStartEnd() {
        return singleStepIncludeStartEnd;
    }

    @Override
    public void setSingleStepIncludeStartEnd(boolean singleStepIncludeStartEnd) {
        this.singleStepIncludeStartEnd = singleStepIncludeStartEnd;
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
    public String dumpTracedMessagesAsXml(String nodeId) {
        logger.log("Dump trace message from breakpoint " + nodeId);
        BacklogTracerEventMessage msg = suspendedBreakpointMessages.get(nodeId);
        if (msg == null) {
            return null;
        }
        return msg.toXml(0);
    }

    @Override
    public String dumpTracedMessagesAsJSon(String nodeId) {
        logger.log("Dump trace message from breakpoint " + nodeId);
        BacklogTracerEventMessage msg = suspendedBreakpointMessages.get(nodeId);
        if (msg == null) {
            return null;
        }
        return msg.toJSon(0);
    }

    @Override
    public long getDebugCounter() {
        return debugCounter.get();
    }

    @Override
    public void resetDebugCounter() {
        logger.log("Reset debug counter");
        debugCounter.set(0);
    }

    public StopWatch beforeProcess(Exchange exchange, Processor processor, NamedNode definition) {
        suspendIfNeeded();
        if (isEnabled() && (hasBreakpoint(definition.getId()) || isSingleStepMode())) {
            StopWatch watch = new StopWatch();
            debugger.beforeProcess(exchange, processor, definition);
            return watch;
        }
        return null;
    }

    public void afterProcess(Exchange exchange, Processor processor, NamedNode definition, long timeTaken) {
        debugger.afterProcess(exchange, processor, definition, timeTaken);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (initialBreakpoints != null) {
            for (String b : initialBreakpoints.split(",")) {
                b = b.trim();
                if (!DefaultBacklogDebugger.BREAKPOINT_ALL_ROUTES.equals(b)) {
                    // BREAKPOINT_ALL_ROUTES are special and handled elsewhere
                    addBreakpoint(b);
                }
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (enabled.get()) {
            disableDebugger();
        }
        clearBreakpoints();
    }

    private void clearBreakpoints() {
        // make sure to clear state and latches is counted down, so we won't have hanging threads
        breakpoints.clear();
        for (SuspendedExchange se : suspendedBreakpoints.values()) {
            se.getLatch().countDown();
        }
        suspendedBreakpoints.clear();
        suspendedBreakpointMessages.clear();
    }

    /**
     * Refresh the content of the existing backlog tracer event message corresponding to the given node id with the new
     * content of exchange.
     *
     * @param nodeId            the node id for the breakpoint
     * @param suspendedExchange the content of the new suspended exchange to use to refresh the backlog tracer event
     *                          message.
     */
    private void refreshBacklogTracerEventMessage(String nodeId, SuspendedExchange suspendedExchange) {
        suspendedBreakpointMessages.computeIfPresent(
                nodeId,
                (nId, message) -> new DefaultBacklogTracerEventMessage(
                        false, false, message.getUid(), message.getTimestamp(), message.getLocation(), message.getRouteId(),
                        message.getToNode(),
                        message.getExchangeId(),
                        false, false,
                        dumpAsXml(suspendedExchange.getExchange()),
                        dumpAsJSon(suspendedExchange.getExchange())));
    }

    /**
     * Dumps the message as a generic XML structure.
     *
     * @param  exchange the exchange to dump as XML
     * @return          the XML
     */
    private String dumpAsXml(Exchange exchange) {
        return MessageHelper.dumpAsXml(exchange.getIn(), includeExchangeProperties, includeExchangeVariables, true, 2, true,
                isBodyIncludeStreams(), isBodyIncludeFiles(),
                getBodyMaxChars());
    }

    /**
     * Dumps the message as a generic JSon structure.
     *
     * @param  exchange the exchange to dump as JSon
     * @return          the JSon
     */
    private String dumpAsJSon(Exchange exchange) {
        return MessageHelper.dumpAsJSon(exchange.getIn(), includeExchangeProperties, includeExchangeVariables, true, 2, true,
                isBodyIncludeStreams(), isBodyIncludeFiles(),
                getBodyMaxChars(), true);
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
        public void beforeProcess(Exchange exchange, Processor processor, NamedNode definition) {
            // store a copy of the message so we can see that from the debugger
            long timestamp = System.currentTimeMillis();
            String toNode = definition.getId();
            String routeId = CamelContextHelper.getRouteId(definition);
            String exchangeId = exchange.getExchangeId();
            String messageAsXml = dumpAsXml(exchange);
            String messageAsJSon = dumpAsJSon(exchange);
            long uid = debugCounter.incrementAndGet();
            String source = LoggerHelper.getLineNumberLoggerName(definition);
            boolean first = "from".equals(definition.getShortName());

            BacklogTracerEventMessage msg
                    = new DefaultBacklogTracerEventMessage(
                            first, false, uid, timestamp, source, routeId, toNode, exchangeId, false, false, messageAsXml,
                            messageAsJSon);
            suspendedBreakpointMessages.put(nodeId, msg);

            // suspend at this breakpoint
            final SuspendedExchange se = suspendedBreakpoints.get(nodeId);
            if (se != null) {
                // now wait until we should continue
                logger.log(String.format("NodeBreakpoint at node %s is waiting to continue for exchangeId: %s", toNode,
                        exchangeId));
                try {
                    boolean hit = se.getLatch().await(fallbackTimeout, TimeUnit.SECONDS);
                    if (!hit) {
                        logger.log(
                                String.format("NodeBreakpoint at node %s timed out and is continued exchangeId: %s", toNode,
                                        exchangeId),
                                LoggingLevel.WARN);
                    } else {
                        logger.log(String.format("NodeBreakpoint at node %s is continued exchangeId: %s", toNode, exchangeId));
                    }
                } catch (InterruptedException e) {
                    // ignore
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public boolean matchProcess(Exchange exchange, Processor processor, NamedNode definition, boolean before) {
            if (!before) {
                // after should not match for node breakpoints
                return false;
            }

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
        public boolean matchEvent(Exchange exchange, ExchangeEvent event) {
            return false;
        }
    }

    /**
     * Represents a {@link org.apache.camel.spi.Breakpoint} that is used during single step mode.
     */
    private final class StepBreakpoint extends BreakpointSupport implements Condition {

        @Override
        public void beforeProcess(Exchange exchange, Processor processor, NamedNode definition) {
            // store a copy of the message so we can see that from the debugger
            long timestamp = System.currentTimeMillis();
            String toNode = definition.getId();
            String routeId = CamelContextHelper.getRouteId(definition);
            String exchangeId = exchange.getExchangeId();
            String messageAsXml = dumpAsXml(exchange);
            String messageAsJSon = dumpAsJSon(exchange);
            long uid = debugCounter.incrementAndGet();
            String source = LoggerHelper.getLineNumberLoggerName(definition);

            BacklogTracerEventMessage msg
                    = new DefaultBacklogTracerEventMessage(
                            false, false, uid, timestamp, source, routeId, toNode, exchangeId, false, false, messageAsXml,
                            messageAsJSon);
            suspendedBreakpointMessages.put(toNode, msg);

            // suspend at this breakpoint
            SuspendedExchange se = new SuspendedExchange(exchange, new CountDownLatch(1));
            suspendedBreakpoints.put(toNode, se);

            // now wait until we should continue
            logger.log(
                    String.format("StepBreakpoint at node %s is waiting to continue for exchangeId: %s", toNode,
                            exchange.getExchangeId()));
            try {
                boolean hit = se.getLatch().await(fallbackTimeout, TimeUnit.SECONDS);
                if (!hit) {
                    logger.log(
                            String.format("StepBreakpoint at node %s timed out and is continued exchangeId: %s", toNode,
                                    exchange.getExchangeId()),
                            LoggingLevel.WARN);
                } else {
                    logger.log(String.format("StepBreakpoint at node %s is continued exchangeId: %s", toNode,
                            exchange.getExchangeId()));
                }
            } catch (InterruptedException e) {
                // ignore
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public boolean matchProcess(Exchange exchange, Processor processor, NamedNode definition, boolean before) {
            // always match in step (both before and after)
            return true;
        }

        @Override
        public boolean matchEvent(Exchange exchange, ExchangeEvent event) {
            return event instanceof ExchangeCompletedEvent || event instanceof CamelEvent.ExchangeFailedEvent;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onEvent(Exchange exchange, ExchangeEvent event, NamedNode definition) {
            if (event instanceof ExchangeCompletedEvent || event instanceof CamelEvent.ExchangeFailedEvent) {
                Throwable cause = null;
                if (event instanceof CamelEvent.ExchangeFailedEvent fe) {
                    cause = fe.getCause();
                }
                NamedRoute route = getOriginalRoute(exchange);
                String completedId = event.getExchange().getExchangeId();
                try {
                    if (isSingleStepIncludeStartEnd() && singleStepExchangeId != null
                            && singleStepExchangeId.equals(completedId)) {
                        doCompleted(exchange, definition, route, cause);
                    }
                } finally {
                    logger.log("ExchangeId: " + completedId + " is completed, so exiting single step mode.");
                    singleStepExchangeId = null;
                }
            }
        }

        private NamedRoute getOriginalRoute(Exchange exchange) {
            List<MessageHistory> list = exchange.getProperty(ExchangePropertyKey.MESSAGE_HISTORY, List.class);
            if (list != null) {
                for (MessageHistory h : list) {
                    NamedNode n = h.getNode();
                    NamedRoute nr = CamelContextHelper.getRoute(n);
                    if (nr != null) {
                        boolean skip = nr.isCreatedFromRest() || nr.isCreatedFromTemplate();
                        if (!skip) {
                            return nr;
                        }
                    }
                }
            }
            return null;
        }

        private void doCompleted(Exchange exchange, NamedNode definition, NamedRoute route, Throwable cause) {
            // create pseudo-last step in single step mode
            long timestamp = System.currentTimeMillis();
            String toNode = CamelContextHelper.getRouteId(definition);
            String routeId = route != null ? route.getRouteId() : toNode;
            String exchangeId = exchange.getExchangeId();
            String messageAsXml = dumpAsXml(exchange);
            String messageAsJSon = dumpAsJSon(exchange);
            long uid = debugCounter.incrementAndGet();
            String source = LoggerHelper.getLineNumberLoggerName(route != null ? route : definition);

            BacklogTracerEventMessage msg
                    = new DefaultBacklogTracerEventMessage(
                            false, true, uid, timestamp, source, routeId, toNode, exchangeId, false, false,
                            messageAsXml,
                            messageAsJSon);

            // we want to capture if there was an exception
            if (cause != null) {
                String xml = MessageHelper.dumpExceptionAsXML(cause, 4);
                msg.setExceptionAsXml(xml);
                String json = MessageHelper.dumpExceptionAsJSon(cause, 4, true);
                msg.setExceptionAsJSon(json);
            }

            suspendedBreakpointMessages.put(toNode, msg);

            // suspend at this breakpoint
            SuspendedExchange se = new SuspendedExchange(exchange, new CountDownLatch(1));
            suspendedBreakpoints.put(toNode, se);

            // now wait until we should continue
            logger.log(
                    String.format("StepBreakpoint at node %s is waiting to continue for exchangeId: %s", toNode,
                            exchange.getExchangeId()));
            try {
                boolean hit = se.getLatch().await(fallbackTimeout, TimeUnit.SECONDS);
                if (!hit) {
                    logger.log(
                            String.format("StepBreakpoint at node %s timed out and is continued exchangeId: %s", toNode,
                                    exchange.getExchangeId()),
                            LoggingLevel.WARN);
                } else {
                    logger.log(String.format("StepBreakpoint at node %s is continued exchangeId: %s", toNode,
                            exchange.getExchangeId()));
                }
            } catch (InterruptedException e) {
                // ignore
                Thread.currentThread().interrupt();
            }
        }
    }

}
