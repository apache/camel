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
package org.apache.camel.management;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Channel;
import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.errorhandler.RedeliveryErrorHandler;
import org.apache.camel.spi.BacklogDebugger;
import org.apache.camel.spi.StepHook;
import org.apache.camel.spi.StepListener;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@link StepListener} runs together with a {@link StepHook} (as camel-chaos uses), JMX, the tracer and the backlog
 * debugger, and each still works: a step that the hook fails is not a call the listener sees, JMX counts each call, the
 * tracer traces and the debugger stops at its breakpoint before the listener sees the step.
 */
@DisabledOnOs(OS.AIX)
class ManagedStepListenerTest extends ManagementTestSupport {

    private final Queue<String> events = new ConcurrentLinkedQueue<>();
    private final AtomicInteger hookAsked = new AtomicInteger();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getCamelContextExtension().addStepListener(new StepListener() {
            @Override
            public Object onStepCall(Exchange exchange, String routeId, String nodeId) {
                events.add("call " + nodeId);
                return nodeId;
            }

            @Override
            public void onStepCallback(Object state, boolean doneSync) {
                events.add("callback " + state);
            }
        });
        context.getCamelContextExtension().addStepHook(this::beforeStep);
        return context;
    }

    private long beforeStep(Exchange exchange, String nodeId) {
        if ("flaky".equals(nodeId) && hookAsked.incrementAndGet() == 1) {
            exchange.setException(new IllegalArgumentException("Failed by the step hook"));
        }
        return StepHook.PROCEED;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setTracing(true);
                context.setDebugging(true);

                from("direct:combo").routeId("combo")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0).logStackTrace(false))
                        .to("mock:flaky").id("flaky")
                        .to("mock:bar").id("bar")
                        .to("mock:result").id("result");
            }
        };
    }

    @Test
    void testTogetherWithStepHookJmxTracerAndDebugger() throws Exception {
        // with JMX and an error handler that can redeliver: error handler, JMX instrumentation, the step hook
        // processor, the listener wrapper, the processor
        Channel channel = findChannel(context.getRoute("combo").navigate(), "flaky");
        assertNotNull(channel);
        RedeliveryErrorHandler errorHandler = assertInstanceOf(RedeliveryErrorHandler.class, channel.getErrorHandler());
        DelegateAsyncProcessor instrumentation
                = assertInstanceOf(DefaultInstrumentationProcessor.class, errorHandler.getOutput());
        DelegateAsyncProcessor hook = assertInstanceOf(DelegateAsyncProcessor.class, instrumentation.getProcessor());
        assertEquals("StepHookProcessor", hook.getClass().getSimpleName());
        DelegateAsyncProcessor wrapper = assertInstanceOf(DelegateAsyncProcessor.class, hook.getProcessor());
        assertEquals("StepListenerProcessor", wrapper.getClass().getSimpleName());
        assertSame(channel.getNextProcessor(), wrapper.getProcessor());

        BacklogDebugger debugger = context.hasService(BacklogDebugger.class);
        assertNotNull(debugger);
        debugger.enableDebugger();
        debugger.addBreakpoint("bar");
        getMockEndpoint("mock:result").expectedMessageCount(1);

        Future<Object> out = template.asyncRequestBody("direct:combo", "Hello");

        // the debugger stops before the step bar, so the listener has not seen it yet
        await().atMost(10, TimeUnit.SECONDS).until(() -> debugger.getSuspendedBreakpointNodeIds().contains("bar"));
        assertFalse(events.contains("call bar"), events.toString());
        debugger.resumeBreakpoint("bar");
        assertEquals("Hello", out.get(10, TimeUnit.SECONDS));
        assertMockEndpointsSatisfied();

        // the hook failed the first attempt of flaky before its processor was called, so the listener sees one call
        assertEquals(2, hookAsked.get());
        assertEquals(List.of("call flaky", "callback flaky", "call bar", "callback bar", "call result",
                "callback result"), new ArrayList<>(events));

        // JMX counts the attempt that the hook failed as a failure of the step
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getCamelObjectName(TYPE_PROCESSOR, "flaky");
        assertEquals(1L, mbeanServer.getAttribute(on, "ExchangesFailed"));
        assertEquals(1L, mbeanServer.getAttribute(on, "ExchangesCompleted"));
        on = getCamelObjectName(TYPE_PROCESSOR, "result");
        assertEquals(1L, mbeanServer.getAttribute(on, "ExchangesCompleted"));

        // the tracer traced the steps
        assertTrue(context.getTracer().getTraceCounter() > 0);
    }

    private Channel findChannel(Object node, String nodeId) {
        if (node instanceof Channel channel) {
            Processor step = context.getRoute("combo").filter(nodeId).get(0);
            if (channel.getNextProcessor() == step) {
                return channel;
            }
        } else if (node instanceof Navigate<?> navigate && navigate.hasNext()) {
            for (Object child : navigate.next()) {
                Channel found = findChannel(child, nodeId);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
