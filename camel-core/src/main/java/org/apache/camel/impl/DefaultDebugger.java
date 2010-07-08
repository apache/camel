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
package org.apache.camel.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.RouteNode;
import org.apache.camel.management.EventNotifierSupport;
import org.apache.camel.management.event.AbstractExchangeEvent;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.spi.Breakpoint;
import org.apache.camel.spi.Condition;
import org.apache.camel.spi.Debugger;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The default implementation of the {@link Debugger}.
 *
 * @version $Revision$
 */
public class DefaultDebugger implements Debugger, CamelContextAware {

    private static final Log LOG = LogFactory.getLog(DefaultDebugger.class);
    private final List<BreakpointConditions> breakpoints = new ArrayList<BreakpointConditions>();
    // TODO: Should we support multiple single steps?
    private final Map<String, Breakpoint> singleSteps = new HashMap<String, Breakpoint>();
    private CamelContext camelContext;

    /**
     * Holder class for breakpoint and the associated conditions
     */
    private final class BreakpointConditions {
        private Breakpoint breakpoint;
        private List<Condition> conditions;

        private BreakpointConditions(Breakpoint breakpoint) {
            this(breakpoint, null);
        }

        private BreakpointConditions(Breakpoint breakpoint, List<Condition> conditions) {
            this.breakpoint = breakpoint;
            this.conditions = conditions;
        }

        public Breakpoint getBreakpoint() {
            return breakpoint;
        }

        public List<Condition> getConditions() {
            return conditions;
        }
    }

    public DefaultDebugger() {
    }

    public DefaultDebugger(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public void addBreakpoint(Breakpoint breakpoint) {
        breakpoints.add(new BreakpointConditions(breakpoint));
    }

    public void addBreakpoint(Breakpoint breakpoint, Condition... conditions) {
        if (conditions != null) {
            breakpoints.add(new BreakpointConditions(breakpoint, Arrays.asList(conditions)));
        } else {
            breakpoints.add(new BreakpointConditions(breakpoint));
        }
    }

    public void addSingleStepBreakpoint(final Breakpoint breakpoint) {
        addSingleStepBreakpoint(breakpoint, null);
    }

    public void addSingleStepBreakpoint(final Breakpoint breakpoint, Condition... conditions) {
        // wrap the breakpoint into single step breakpoint so we can automatic enable/disable the single step mode
        Breakpoint singlestep = new Breakpoint() {
            public State getState() {
                return breakpoint.getState();
            }

            public void suspend() {
                breakpoint.suspend();
            }

            public void activate() {
                breakpoint.activate();
            }

            public void beforeProcess(Exchange exchange, Processor processor, ProcessorDefinition definition) {
                breakpoint.beforeProcess(exchange, processor, definition);
            }

            public void afterProcess(Exchange exchange, Processor processor, ProcessorDefinition definition, long timeTaken) {
                breakpoint.afterProcess(exchange, processor, definition, timeTaken);
            }

            public void onEvent(Exchange exchange, EventObject event, ProcessorDefinition definition) {
                if (event instanceof ExchangeCreatedEvent) {
                    exchange.getContext().getDebugger().startSingleStepExchange(exchange.getExchangeId(), this);
                } else if (event instanceof ExchangeCompletedEvent) {
                    exchange.getContext().getDebugger().stopSingleStepExchange(exchange.getExchangeId());
                }
                breakpoint.onEvent(exchange, event, definition);
            }

            @Override
            public String toString() {
                return breakpoint.toString();
            }
        };

        addBreakpoint(singlestep, conditions);
    }

    public void removeBreakpoint(Breakpoint breakpoint) {
        breakpoints.remove(breakpoint);
    }

    public void suspendAllBreakpoints() {
        for (BreakpointConditions breakpoint : breakpoints) {
            breakpoint.getBreakpoint().suspend();
        }
    }

    public void activateAllBreakpoints() {
        for (BreakpointConditions breakpoint : breakpoints) {
            breakpoint.getBreakpoint().activate();
        }
    }

    public List<Breakpoint> getBreakpoints() {
        List<Breakpoint> answer = new ArrayList<Breakpoint>(breakpoints.size());
        for (BreakpointConditions e : breakpoints) {
            answer.add(e.getBreakpoint());
        }
        return Collections.unmodifiableList(answer);
    }

    public void startSingleStepExchange(String exchangeId, Breakpoint breakpoint) {
        singleSteps.put(exchangeId, breakpoint);
    }

    public void stopSingleStepExchange(String exchangeId) {
        singleSteps.remove(exchangeId);
    }

    public boolean beforeProcess(Exchange exchange, Processor processor, ProcessorDefinition definition) {
        // is the exchange in single step mode?
        Breakpoint singleStep = singleSteps.get(exchange.getExchangeId());
        if (singleStep != null) {
            onBeforeProcess(exchange, processor, definition, singleStep);
            return true;
        }

        // does any of the breakpoints apply?
        boolean match = false;
        for (BreakpointConditions breakpoint : breakpoints) {
            // breakpoint must be active
            if (Breakpoint.State.Active.equals(breakpoint.getBreakpoint().getState())) {
                if (matchConditions(exchange, processor, definition, breakpoint)) {
                    match = true;
                    onBeforeProcess(exchange, processor, definition, breakpoint.getBreakpoint());
                }
            }
        }

        return match;
    }

    public boolean afterProcess(Exchange exchange, Processor processor, ProcessorDefinition definition, long timeTaken) {
        // is the exchange in single step mode?
        Breakpoint singleStep = singleSteps.get(exchange.getExchangeId());
        if (singleStep != null) {
            onAfterProcess(exchange, processor, definition, timeTaken, singleStep);
            return true;
        }

        // does any of the breakpoints apply?
        boolean match = false;
        for (BreakpointConditions breakpoint : breakpoints) {
            // breakpoint must be active
            if (Breakpoint.State.Active.equals(breakpoint.getBreakpoint().getState())) {
                if (matchConditions(exchange, processor, definition, breakpoint)) {
                    match = true;
                    onAfterProcess(exchange, processor, definition, timeTaken, breakpoint.getBreakpoint());
                }
            }
        }

        return match;
    }

    public boolean onEvent(Exchange exchange, EventObject event) {
        // is the exchange in single step mode?
        Breakpoint singleStep = singleSteps.get(exchange.getExchangeId());
        if (singleStep != null) {
            onEvent(exchange, event, singleStep);
            return true;
        }

        // does any of the breakpoints apply?
        boolean match = false;
        for (BreakpointConditions breakpoint : breakpoints) {
            // breakpoint must be active
            if (Breakpoint.State.Active.equals(breakpoint.getBreakpoint().getState())) {
                if (matchConditions(exchange, event, breakpoint)) {
                    match = true;
                    onEvent(exchange, event, breakpoint.getBreakpoint());
                }
            }
        }

        return match;
    }

    protected void onBeforeProcess(Exchange exchange, Processor processor, ProcessorDefinition definition, Breakpoint breakpoint) {
        try {
            breakpoint.beforeProcess(exchange, processor, definition);
        } catch (Throwable e) {
            LOG.warn("Exception occurred in breakpoint: " + breakpoint + ". This exception will be ignored.", e);
        }
    }

    protected void onAfterProcess(Exchange exchange, Processor processor, ProcessorDefinition definition, long timeTaken, Breakpoint breakpoint) {
        try {
            breakpoint.afterProcess(exchange, processor, definition, timeTaken);
        } catch (Throwable e) {
            LOG.warn("Exception occurred in breakpoint: " + breakpoint + ". This exception will be ignored.", e);
        }
    }

    protected void onEvent(Exchange exchange, EventObject event, Breakpoint breakpoint) {
        ProcessorDefinition definition = null;

        // try to get the last known definition
        if (exchange.getUnitOfWork() != null && exchange.getUnitOfWork().getTracedRouteNodes() != null) {
            RouteNode node = exchange.getUnitOfWork().getTracedRouteNodes().getLastNode();
            if (node != null) {
                definition = node.getProcessorDefinition();
            }
        }

        try {
            breakpoint.onEvent(exchange, event, definition);
        } catch (Throwable e) {
            LOG.warn("Exception occurred in breakpoint: " + breakpoint + ". This exception will be ignored.", e);
        }
    }

    private boolean matchConditions(Exchange exchange, Processor processor, ProcessorDefinition definition, BreakpointConditions breakpoint) {
        if (breakpoint.getConditions() != null && !breakpoint.getConditions().isEmpty()) {
            for (Condition condition : breakpoint.getConditions()) {
                if (!condition.matchProcess(exchange, processor, definition)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean matchConditions(Exchange exchange, EventObject event, BreakpointConditions breakpoint) {
        if (breakpoint.getConditions() != null && !breakpoint.getConditions().isEmpty()) {
            for (Condition condition : breakpoint.getConditions()) {
                if (!condition.matchEvent(exchange, event)) {
                    return false;
                }
            }
        }

        return true;
    }

    public void start() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext", this);
        // register our event notifier
        camelContext.getManagementStrategy().addEventNotifier(new DebugEventNotifier());
        Tracer tracer = Tracer.getTracer(camelContext);
        if (tracer == null) {
            // tracer is disabled so enable it silently so we can leverage it to trace the Exchanges for us
            tracer = Tracer.createTracer(camelContext);
            tracer.setLogLevel(LoggingLevel.OFF);
            camelContext.addService(tracer);
            camelContext.addInterceptStrategy(tracer);
        }
    }

    public void stop() throws Exception {
        breakpoints.clear();
        singleSteps.clear();
    }

    @Override
    public String toString() {
        return "DefaultDebugger";
    }

    private final class DebugEventNotifier extends EventNotifierSupport {

        private DebugEventNotifier() {
            setIgnoreCamelContextEvents(true);
            setIgnoreServiceEvents(true);
        }

        public void notify(EventObject event) throws Exception {
            AbstractExchangeEvent aee = (AbstractExchangeEvent) event;
            Exchange exchange = aee.getExchange();
            onEvent(exchange, event);

            if (event instanceof ExchangeCompletedEvent) {
                // failsafe to ensure we remote single steps when the Exchange is complete
                singleSteps.remove(exchange.getExchangeId());
            }
        }

        public boolean isEnabled(EventObject event) {
            return event instanceof AbstractExchangeEvent;
        }

        @Override
        protected void doStart() throws Exception {
            // noop
        }

        @Override
        protected void doStop() throws Exception {
            // noop
        }
    }
}
