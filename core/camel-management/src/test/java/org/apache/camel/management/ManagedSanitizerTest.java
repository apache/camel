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

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.sanitizer.Sanitizer;
import org.apache.camel.sanitizer.SanitizerViolation;
import org.apache.camel.sanitizer.Violation;
import org.apache.camel.sanitizer.junit.ExpectSanitizerViolation;
import org.apache.camel.spi.BacklogDebugger;
import org.apache.camel.spi.StepHook;
import org.apache.camel.support.AsyncProcessorSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The exchange sanitizer together with the other development features: a {@link StepHook} that fails a step (as
 * camel-chaos does), JMX, the tracer and the backlog debugger. Each still works, and the sanitizer reports only what it
 * should: a step that the hook fails is not a call of its processor, and a processor that throws is reported.
 */
@DisabledOnOs(OS.AIX)
class ManagedSanitizerTest extends ManagementTestSupport {

    private final AtomicInteger hookAsked = new AtomicInteger();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().addOverrideProperty("camel.sanitizer.enabled", "true");
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

                from("direct:broken").routeId("broken")
                        .process(new AsyncProcessorSupport() {
                            @Override
                            public boolean process(Exchange exchange, AsyncCallback callback) {
                                throw new IllegalStateException("Forced");
                            }
                        }).id("throws");
            }
        };
    }

    @Test
    // charged to this class only when the CI switch runs the extension for it
    @ExpectSanitizerViolation(value = Violation.THREW_BEFORE_CALLBACK, step = "throws")
    void testTogetherWithStepHookJmxTracerAndDebugger() throws Exception {
        Sanitizer sanitizer = Sanitizer.get(context);
        assertNotNull(sanitizer);

        BacklogDebugger debugger = context.hasService(BacklogDebugger.class);
        assertNotNull(debugger);
        debugger.enableDebugger();
        debugger.addBreakpoint("bar");
        getMockEndpoint("mock:result").expectedMessageCount(1);

        Future<Object> out = template.asyncRequestBody("direct:combo", "Hello");
        await().atMost(10, TimeUnit.SECONDS).until(() -> debugger.getSuspendedBreakpointNodeIds().contains("bar"));
        debugger.resumeBreakpoint("bar");
        assertEquals("Hello", out.get(10, TimeUnit.SECONDS));
        assertMockEndpointsSatisfied();

        // the hook failed the first attempt of flaky before its processor was called: the sanitizer checked the call of
        // the redelivery, bar and result, and reported nothing
        assertEquals(2, hookAsked.get());
        assertEquals(3, sanitizer.getCallCount());
        assertEquals(0, sanitizer.getViolations().size());

        // JMX counts the attempt that the hook failed, and the tracer traced
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getCamelObjectName(TYPE_PROCESSOR, "flaky");
        assertEquals(1L, mbeanServer.getAttribute(on, "ExchangesFailed"));
        assertEquals(1L, mbeanServer.getAttribute(on, "ExchangesCompleted"));
        assertTrue(context.getTracer().getTraceCounter() > 0);

        // a processor that throws is reported, and JMX counts the failure of its step
        Exchange failed = template.send("direct:broken", e -> e.getMessage().setBody("x"));
        assertNotNull(failed.getException());
        assertEquals(1, sanitizer.getViolations().size());
        SanitizerViolation v = sanitizer.getViolations().get(0);
        assertEquals(Violation.THREW_BEFORE_CALLBACK, v.getKind());
        assertEquals("throws", v.getStepId());
        on = getCamelObjectName(TYPE_PROCESSOR, "throws");
        assertEquals(1L, mbeanServer.getAttribute(on, "ExchangesFailed"));
    }
}
