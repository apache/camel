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
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.Breakpoint;
import org.apache.camel.spi.Condition;
import org.apache.camel.spi.Debugger;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The default implementation of the {@link Debugger}.
 *
 * @version $Revision$
 */
public class DefaultDebugger implements Debugger {

    private static final Log LOG = LogFactory.getLog(DefaultDebugger.class);
    private final List<BreakpointConditions> breakpoints = new ArrayList<BreakpointConditions>();

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

    public void addBreakpoint(Breakpoint breakpoint) {
        breakpoints.add(new BreakpointConditions(breakpoint));
    }

    public void addBreakpoint(Breakpoint breakpoint, Condition... conditions) {
        breakpoints.add(new BreakpointConditions(breakpoint, Arrays.asList(conditions)));
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

    public boolean onExchange(Exchange exchange, Processor processor, ProcessorDefinition definition) {
        boolean match = false;

        // does any of the breakpoints apply?
        for (BreakpointConditions breakpoint : breakpoints) {
            // breakpoint must be active
            if (Breakpoint.State.Active.equals(breakpoint.getBreakpoint().getState())) {
                if (matchConditions(exchange, processor, definition, breakpoint)) {
                    match = true;
                    onBreakpoint(exchange, processor, definition, breakpoint.getBreakpoint());
                }
            }
        }

        return match;
    }

    private boolean matchConditions(Exchange exchange,  Processor processor, ProcessorDefinition definition, BreakpointConditions breakpoint) {
        if (breakpoint.getConditions() != null && !breakpoint.getConditions().isEmpty()) {
            for (Condition condition : breakpoint.getConditions()) {
                if (!condition.match(exchange, definition)) {
                    return false;
                }
            }
        }

        return true;
    }

    protected void onBreakpoint(Exchange exchange, Processor processor, ProcessorDefinition definition, Breakpoint breakpoint) {
        breakpoint.onExchange(exchange, processor, definition);
    }

    public void start() throws Exception {
        // noop
    }

    public void stop() throws Exception {
        breakpoints.clear();
        // noop
    }

    @Override
    public String toString() {
        return "DefaultDebugger";
    }
}
