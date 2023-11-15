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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.MessageHistory;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.spi.Breakpoint;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCreatedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeEvent;
import org.apache.camel.spi.Condition;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * The default implementation of the {@link Debugger}.
 */
public class DefaultDebugger extends ServiceSupport implements Debugger, CamelContextAware {

    private final EventNotifier debugEventNotifier = new DebugEventNotifier();
    private final List<BreakpointConditions> breakpoints = new CopyOnWriteArrayList<>();
    private final int maxConcurrentSingleSteps = 1;
    private final Map<String, Breakpoint> singleSteps = new HashMap<>(maxConcurrentSingleSteps);
    private CamelContext camelContext;

    /**
     * Holder class for breakpoint and the associated conditions
     */
    private static final class BreakpointConditions {
        private final Breakpoint breakpoint;
        private final List<Condition> conditions;

        private BreakpointConditions(Breakpoint breakpoint) {
            this(breakpoint, new ArrayList<>());
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

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void addBreakpoint(Breakpoint breakpoint) {
        breakpoints.add(new BreakpointConditions(breakpoint));
    }

    @Override
    public void addBreakpoint(Breakpoint breakpoint, Condition... conditions) {
        breakpoints.add(new BreakpointConditions(breakpoint, Arrays.asList(conditions)));
    }

    @Override
    public void addSingleStepBreakpoint(final Breakpoint breakpoint) {
        addSingleStepBreakpoint(breakpoint, new Condition[] {});
    }

    @Override
    public void addSingleStepBreakpoint(final Breakpoint breakpoint, Condition... conditions) {
        // wrap the breakpoint into single step breakpoint so we can automatic enable/disable the single step mode
        Breakpoint singlestep = new Breakpoint() {
            @Override
            public State getState() {
                return breakpoint.getState();
            }

            @Override
            public void suspend() {
                breakpoint.suspend();
            }

            @Override
            public void activate() {
                breakpoint.activate();
            }

            @Override
            public void beforeProcess(Exchange exchange, Processor processor, NamedNode definition) {
                breakpoint.beforeProcess(exchange, processor, definition);
            }

            @Override
            public void afterProcess(Exchange exchange, Processor processor, NamedNode definition, long timeTaken) {
                breakpoint.afterProcess(exchange, processor, definition, timeTaken);
            }

            @Override
            public void onEvent(Exchange exchange, ExchangeEvent event, NamedNode definition) {
                if (event instanceof ExchangeCreatedEvent) {
                    startSingleStepExchange(exchange.getExchangeId(), this);
                }
                try {
                    breakpoint.onEvent(exchange, event, definition);
                } finally {
                    if (event instanceof ExchangeCompletedEvent) {
                        stopSingleStepExchange(exchange.getExchangeId());
                    }
                }
            }

            @Override
            public String toString() {
                return breakpoint.toString();
            }
        };

        addBreakpoint(singlestep, conditions);
    }

    @Override
    public void removeBreakpoint(Breakpoint breakpoint) {
        for (BreakpointConditions condition : breakpoints) {
            if (condition.getBreakpoint().equals(breakpoint)) {
                breakpoints.remove(condition);
            }
        }
    }

    @Override
    public void suspendAllBreakpoints() {
        for (BreakpointConditions breakpoint : breakpoints) {
            breakpoint.getBreakpoint().suspend();
        }
    }

    @Override
    public void activateAllBreakpoints() {
        for (BreakpointConditions breakpoint : breakpoints) {
            breakpoint.getBreakpoint().activate();
        }
    }

    @Override
    public List<Breakpoint> getBreakpoints() {
        List<Breakpoint> answer = new ArrayList<>(breakpoints.size());
        for (BreakpointConditions e : breakpoints) {
            answer.add(e.getBreakpoint());
        }
        return Collections.unmodifiableList(answer);
    }

    @Override
    public boolean startSingleStepExchange(String exchangeId, Breakpoint breakpoint) {
        // can we accept single stepping the given exchange?
        if (singleSteps.size() >= maxConcurrentSingleSteps) {
            return false;
        }

        singleSteps.put(exchangeId, breakpoint);
        return true;
    }

    @Override
    public void stopSingleStepExchange(String exchangeId) {
        // completed so we need a "last" event
        singleSteps.remove(exchangeId);
    }

    @Override
    public boolean beforeProcess(Exchange exchange, Processor processor, NamedNode definition) {
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
                if (matchConditions(exchange, processor, definition, breakpoint, true)) {
                    match = true;
                    onBeforeProcess(exchange, processor, definition, breakpoint.getBreakpoint());
                }
            }
        }

        return match;
    }

    @Override
    public boolean afterProcess(Exchange exchange, Processor processor, NamedNode definition, long timeTaken) {
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
                if (matchConditions(exchange, processor, definition, breakpoint, false)) {
                    match = true;
                    onAfterProcess(exchange, processor, definition, timeTaken, breakpoint.getBreakpoint());
                }
            }
        }

        return match;
    }

    @Override
    public boolean onEvent(Exchange exchange, ExchangeEvent event) {
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

    protected void onBeforeProcess(Exchange exchange, Processor processor, NamedNode definition, Breakpoint breakpoint) {
        try {
            breakpoint.beforeProcess(exchange, processor, definition);
        } catch (Exception e) {
            // ignore
        }
    }

    protected void onAfterProcess(
            Exchange exchange, Processor processor, NamedNode definition, long timeTaken, Breakpoint breakpoint) {
        try {
            breakpoint.afterProcess(exchange, processor, definition, timeTaken);
        } catch (Exception e) {
            // ignore
        }
    }

    @SuppressWarnings("unchecked")
    protected void onEvent(Exchange exchange, ExchangeEvent event, Breakpoint breakpoint) {
        // try to get the last known definition
        List<MessageHistory> list = exchange.getProperty(ExchangePropertyKey.MESSAGE_HISTORY, List.class);
        MessageHistory last = list != null ? list.get(list.size() - 1) : null;
        NamedNode definition = last != null ? last.getNode() : null;

        try {
            breakpoint.onEvent(exchange, event, definition);
        } catch (Exception e) {
            // ignore
        }
    }

    private boolean matchConditions(
            Exchange exchange, Processor processor, NamedNode definition, BreakpointConditions breakpoint, boolean before) {
        for (Condition condition : breakpoint.getConditions()) {
            if (!condition.matchProcess(exchange, processor, definition, before)) {
                return false;
            }
        }

        return true;
    }

    private boolean matchConditions(Exchange exchange, ExchangeEvent event, BreakpointConditions breakpoint) {
        for (Condition condition : breakpoint.getConditions()) {
            if (!condition.matchEvent(exchange, event)) {
                return false;
            }
        }

        return true;
    }

    @Override
    protected void doInit() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext", this);

        // must have message history enabled when using this debugger
        if (!camelContext.isMessageHistory()) {
            camelContext.setMessageHistory(true);
        }

        // register our event notifier
        camelContext.getManagementStrategy().addEventNotifier(debugEventNotifier);

        ServiceHelper.initService(debugEventNotifier);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(debugEventNotifier);
    }

    @Override
    protected void doStop() throws Exception {
        breakpoints.clear();
        singleSteps.clear();
        ServiceHelper.stopService(debugEventNotifier);
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

        @Override
        public void notify(CamelEvent event) throws Exception {
            ExchangeEvent aee = (ExchangeEvent) event;
            Exchange exchange = aee.getExchange();
            onEvent(exchange, aee);

            if (event instanceof ExchangeCompletedEvent) {
                // fail safe to ensure we remove single steps when the Exchange is complete
                singleSteps.remove(exchange.getExchangeId());
            }
        }

        @Override
        public boolean isEnabled(CamelEvent event) {
            return event instanceof ExchangeEvent;
        }
    }

}
