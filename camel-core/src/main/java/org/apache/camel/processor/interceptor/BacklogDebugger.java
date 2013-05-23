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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
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
 * A {@link org.apache.camel.spi.Debugger} that should be used together with the {@link BacklogTracer} to
 * offer debugging and tracing functionality.
 */
public class BacklogDebugger extends ServiceSupport implements InterceptStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(BacklogDebugger.class);

    private final CamelContext camelContext;
    private LoggingLevel loggingLevel = LoggingLevel.INFO;
    private final CamelLogger logger = new CamelLogger(LOG, loggingLevel);
    private final AtomicBoolean enabled = new AtomicBoolean();
    private final AtomicLong debugCounter = new AtomicLong(0);
    private final Debugger debugger;
    private final Map<String, NodeBreakpoint> breakpoints = new HashMap<String, NodeBreakpoint>();
    private final Map<String, CountDownLatch> suspendedBreakpoints = new HashMap<String, CountDownLatch>();
    private final Map<String, BacklogTracerEventMessage> suspendedBreakpointMessages = new HashMap<String, BacklogTracerEventMessage>();
    private volatile String singleStepExchangeId;

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

        // make sure to clear state and latches is counted down so we wont have hanging threads
        breakpoints.clear();
        for (CountDownLatch latch : suspendedBreakpoints.values()) {
            latch.countDown();
        }
        suspendedBreakpoints.clear();
        suspendedBreakpointMessages.clear();
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
            debugger.addBreakpoint(breakpoint);
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
        CountDownLatch latch = suspendedBreakpoints.remove(nodeId);
        NodeBreakpoint breakpoint = breakpoints.remove(nodeId);
        if (breakpoint != null) {
            debugger.removeBreakpoint(breakpoint);
        }
        if (latch != null) {
            latch.countDown();
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
        CountDownLatch latch = suspendedBreakpoints.remove(nodeId);
        if (latch != null) {
            latch.countDown();
        }
    }

    public void resumeAll() {
        logger.log("Resume all");
        // stop single stepping
        singleStepExchangeId = null;

        for (String node : getSuspendedBreakpointNodeIds()) {
            // remember to remove the dumped message as its no longer in need
            suspendedBreakpointMessages.remove(node);
            CountDownLatch latch = suspendedBreakpoints.remove(node);
            if (latch != null) {
                latch.countDown();
            }
        }
    }

    public void stepBreakpoint(String nodeId) {
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
            CountDownLatch latch = suspendedBreakpoints.remove(node);
            if (latch != null) {
                latch.countDown();
            }
        }
    }

    public Set<String> getSuspendedBreakpointNodeIds() {
        return new LinkedHashSet<String>(suspendedBreakpoints.keySet());
    }

    public void suspendBreakpoint(String nodeId) {
        logger.log("Suspend breakpoint " + nodeId);
        NodeBreakpoint breakpoint = breakpoints.get(nodeId);
        if (breakpoint != null) {
            breakpoint.suspend();
        }
    }

    public void activateBreakpoint(String nodeId) {
        logger.log("Activate breakpoint " + nodeId);
        NodeBreakpoint breakpoint = breakpoints.get(nodeId);
        if (breakpoint != null) {
            breakpoint.activate();
        }
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
        return debugger.afterProcess(exchange, processor, definition, timeTaken);
    }

    protected void doStart() throws Exception {
        // noop
    }

    protected void doStop() throws Exception {
        disableDebugger();
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

        public String getNodeId() {
            return nodeId;
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
            String toNode = nodeId;
            String routeId = ProcessorDefinitionHelper.getRouteId(definition);
            String exchangeId = exchange.getExchangeId();
            String messageAsXml = MessageHelper.dumpAsXml(exchange.getIn(), true, 2, false, false, 1000);
            long uid = debugCounter.incrementAndGet();

            BacklogTracerEventMessage msg = new DefaultBacklogTracerEventMessage(uid, timestamp, routeId, toNode, exchangeId, messageAsXml);
            suspendedBreakpointMessages.put(nodeId, msg);

            // mark as suspend
            final CountDownLatch latch = new CountDownLatch(1);
            suspendedBreakpoints.put(nodeId, latch);

            // now wait until we should continue
            logger.log("NodeBreakpoint at node " + nodeId + " is waiting to continue for exchangeId: " + exchange.getExchangeId());
            try {
                // TODO: have a fallback timeout so we wont wait forever
                latch.await();
                logger.log("NodeBreakpoint at node " + nodeId + " is continued exchangeId: " + exchange.getExchangeId());
            } catch (InterruptedException e) {
                // ignore
            }
            super.beforeProcess(exchange, processor, definition);
        }

        @Override
        public boolean matchProcess(Exchange exchange, Processor processor, ProcessorDefinition<?> definition) {
            boolean match = nodeId.equals(definition.getId());
            if (match && condition != null) {
                return condition.matches(exchange);
            }
            return match;
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
            String messageAsXml = MessageHelper.dumpAsXml(exchange.getIn(), true, 2, false, false, 1000);
            long uid = debugCounter.incrementAndGet();

            BacklogTracerEventMessage msg = new DefaultBacklogTracerEventMessage(uid, timestamp, routeId, toNode, exchangeId, messageAsXml);
            suspendedBreakpointMessages.put(toNode, msg);

            // mark as suspend
            final CountDownLatch latch = new CountDownLatch(1);
            suspendedBreakpoints.put(toNode, latch);

            // now wait until we should continue
            logger.log("StepBreakpoint at node " + toNode + " is waiting to continue for exchangeId: " + exchange.getExchangeId());
            try {
                // TODO: have a fallback timeout so we wont wait forever
                latch.await();
                logger.log("StepBreakpoint at node " + toNode + " is continued exchangeId: " + exchange.getExchangeId());
            } catch (InterruptedException e) {
                // ignore
            }
            super.beforeProcess(exchange, processor, definition);
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
