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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.impl.debugger.BacklogTracer;
import org.apache.camel.impl.debugger.DefaultBacklogDebugger;
import org.apache.camel.impl.debugger.DefaultDebugger;
import org.apache.camel.impl.engine.CamelInternalProcessor.BacklogDebuggerAdvice;
import org.apache.camel.impl.engine.CamelInternalProcessor.BacklogTracerAdvice;
import org.apache.camel.impl.engine.CamelInternalProcessor.DebuggerAdvice;
import org.apache.camel.impl.engine.CamelInternalProcessor.DelayerAdvice;
import org.apache.camel.impl.engine.CamelInternalProcessor.TracingAdvice;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.spi.CamelInternalProcessorAdvice;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The statistics of a node start at the end time of the node before it, which is kept on the exchange. An advice that
 * runs between two nodes and does work (sleeps, suspends at a breakpoint, dumps the message) must clear that time, or
 * its work is counted in the next node. An advice that does no work must keep it, as reading the clock is the cost that
 * the end time saves.
 */
public class CamelInternalProcessorAdviceLastNodeEndTest extends ContextTestSupport {

    private static final long END = 42;

    private final ToDefinition definition = new ToDefinition("mock:result");
    private Exchange exchange;

    @BeforeEach
    public void createExchange() {
        definition.setId("to1");
        exchange = new DefaultExchange(context);
    }

    @Test
    public void testDelayerClearsTheEndTime() throws Exception {
        assertClearedByBefore(new DelayerAdvice(0));
    }

    @Test
    public void testTracerClearsTheEndTimeWhenItTraces() throws Exception {
        DefaultTracer tracer = new DefaultTracer();
        tracer.setCamelContext(context);
        tracer.setEnabled(true);
        TracingAdvice advice = new TracingAdvice(context, tracer, definition, null, false);

        assertClearedByBeforeAndAfter(advice);
    }

    @Test
    public void testTracerKeepsTheEndTimeWhenItDoesNotTrace() throws Exception {
        DefaultTracer tracer = new DefaultTracer();
        tracer.setCamelContext(context);
        tracer.setEnabled(false);
        TracingAdvice advice = new TracingAdvice(context, tracer, definition, null, false);

        assertKept(advice);
    }

    @Test
    public void testBacklogTracerClearsTheEndTimeWhenItTraces() throws Exception {
        BacklogTracer tracer = BacklogTracer.createTracer(context);
        tracer.setEnabled(true);
        BacklogTracerAdvice advice = new BacklogTracerAdvice(context, tracer, definition, null);

        assertClearedByBeforeAndAfter(advice);
    }

    @Test
    public void testBacklogTracerKeepsTheEndTimeWhenItDoesNotTrace() throws Exception {
        BacklogTracer tracer = BacklogTracer.createTracer(context);
        tracer.setEnabled(false);
        BacklogTracerAdvice advice = new BacklogTracerAdvice(context, tracer, definition, null);

        assertKept(advice);
    }

    @Test
    public void testBacklogDebuggerClearsTheEndTime() throws Exception {
        // the debugger can suspend the exchange whether or not it stops at this node
        DefaultBacklogDebugger debugger = (DefaultBacklogDebugger) DefaultBacklogDebugger.createDebugger(context);
        BacklogDebuggerAdvice advice = new BacklogDebuggerAdvice(debugger, null, definition);

        assertClearedByBefore(advice);
        exchange.getExchangeExtension().setLastNodeEndNanos(END);
        advice.after(exchange, new StopWatch());
        assertEquals(0, exchange.getExchangeExtension().getLastNodeEndNanos());
    }

    @Test
    public void testDebuggerClearsTheEndTime() throws Exception {
        DebuggerAdvice advice = new DebuggerAdvice(new DefaultDebugger(context), null, definition);

        assertClearedByBeforeAndAfter(advice);
    }

    private <T> T assertClearedByBefore(CamelInternalProcessorAdvice<T> advice) throws Exception {
        exchange.getExchangeExtension().setLastNodeEndNanos(END);
        T state = advice.before(exchange);
        assertEquals(0, exchange.getExchangeExtension().getLastNodeEndNanos());
        return state;
    }

    private <T> void assertClearedByBeforeAndAfter(CamelInternalProcessorAdvice<T> advice) throws Exception {
        T state = assertClearedByBefore(advice);
        assertNotNull(state, "the advice must have done its work");

        // the next node starts after the after of this advice
        exchange.getExchangeExtension().setLastNodeEndNanos(END);
        advice.after(exchange, state);
        assertEquals(0, exchange.getExchangeExtension().getLastNodeEndNanos());
    }

    private <T> void assertKept(CamelInternalProcessorAdvice<T> advice) throws Exception {
        exchange.getExchangeExtension().setLastNodeEndNanos(END);
        T state = advice.before(exchange);
        assertNull(state, "the advice must not have done any work");
        advice.after(exchange, state);
        assertEquals(END, exchange.getExchangeExtension().getLastNodeEndNanos());
    }
}
