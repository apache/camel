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

import java.util.EventObject;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.BreakpointSupport;
import org.apache.camel.impl.DefaultDebugger;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.Condition;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.ServiceSupport;
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

    private final AtomicBoolean enabled = new AtomicBoolean();
    private final Debugger debugger;
    private final Map<String, NodeBreakpoint> breakpoints = new HashMap<String, NodeBreakpoint>();
    private final Map<String, CountDownLatch> suspendedBreakpoints = new HashMap<String, CountDownLatch>();

    public BacklogDebugger() {
        this.debugger = new DefaultDebugger();
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

    public void enableDebugger() {
        try {
            // TODO: should not start debugger as its old-schoold tracer depedent
            //debugger.start();
            enabled.set(true);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    public void disableDebugger() {
        try {
            enabled.set(false);
            debugger.stop();
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }

        // make sure to clear state and latches is counted down so we wont have hanging threads
        breakpoints.clear();
        for (CountDownLatch latch : suspendedBreakpoints.values()) {
            latch.countDown();
        }
        suspendedBreakpoints.clear();
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public boolean hasBreakpoint(String nodeId) {
        return breakpoints.containsKey(nodeId);
    }

    public void addBreakpoint(String nodeId) {
        if (!breakpoints.containsKey(nodeId)) {
            NodeBreakpoint breakpoint = new NodeBreakpoint(nodeId);
            breakpoints.put(nodeId, breakpoint);
            debugger.addBreakpoint(breakpoint);
        }
    }

    public void removeBreakpoint(String nodeId) {
        // when removing a break point then ensure latches is cleared and counted down so we wont have hanging threads
        CountDownLatch latch = suspendedBreakpoints.remove(nodeId);
        NodeBreakpoint breakpoint = breakpoints.remove(nodeId);
        if (breakpoint != null) {
            debugger.removeBreakpoint(breakpoint);
        }
        if (latch != null) {
            latch.countDown();
        }
    }

    public void continueBreakpoint(String nodeId) {
        CountDownLatch latch = suspendedBreakpoints.remove(nodeId);
        if (latch != null) {
            latch.countDown();
        }
    }

    public Set<String> getSuspendedBreakpointNodeIds() {
        return new LinkedHashSet<String>(suspendedBreakpoints.keySet());
    }

    public boolean beforeProcess(Exchange exchange, Processor processor, ProcessorDefinition<?> definition) {
        return debugger.beforeProcess(exchange, processor, definition);
    }

    public boolean afterProcess(Exchange exchange, Processor processor, ProcessorDefinition<?> definition, long timeTaken) {
        return debugger.afterProcess(exchange, processor, definition, timeTaken);
    }

    protected void doStart() throws Exception {
        ServiceHelper.startService(debugger);
    }

    protected void doStop() throws Exception {
        disableDebugger();
        ServiceHelper.stopServices(debugger);
    }

    /**
     * Represents a {@link org.apache.camel.spi.Breakpoint} that has a {@link Condition} on a specific node id.
     */
    private final class NodeBreakpoint extends BreakpointSupport implements Condition {

        private final String nodeId;

        public NodeBreakpoint(String nodeId) {
            this.nodeId = nodeId;
        }

        @Override
        public void beforeProcess(Exchange exchange, Processor processor, ProcessorDefinition<?> definition) {
            // mark as suspend
            final CountDownLatch latch = new CountDownLatch(1);
            suspendedBreakpoints.put(nodeId, latch);

            // now wait until we should continue
            // TODO: have a fallback timeout so we wont wait forever
            if (LOG.isInfoEnabled()) {
                LOG.info("Breakpoint at node {} is waiting to continue for exchangeId: {}", nodeId, exchange.getExchangeId());
            }
            try {
                latch.await();
                LOG.info("Breakpoint at node {} is continued for exchangeId: {}", nodeId, exchange.getExchangeId());
            } catch (InterruptedException e) {
                // ignore
            }
            super.beforeProcess(exchange, processor, definition);
        }

        @Override
        public boolean matchProcess(Exchange exchange, Processor processor, ProcessorDefinition<?> definition) {
            return nodeId.equals(definition.getId());
        }

        @Override
        public boolean matchEvent(Exchange exchange, EventObject event) {
            return false;
        }
    }
}
