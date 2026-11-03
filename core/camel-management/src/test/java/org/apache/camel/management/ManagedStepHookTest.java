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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.StepHook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ROUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JMX statistics of a step count a failure or a delay from the {@link StepHook} as for the processor of the step,
 * whether the error handler of the step can redeliver (JMX then wraps each call of the processor) or not (JMX is then
 * an advice of the channel).
 */
@DisabledOnOs(OS.AIX)
class ManagedStepHookTest extends ManagementTestSupport {

    // per node id: how many times the hook fails the step, and a delay
    private final Map<String, Integer> failures = new ConcurrentHashMap<>();
    private final Map<String, Long> delays = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> asked = new ConcurrentHashMap<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getCamelContextExtension().setStepHook(this::beforeStep);
        return context;
    }

    private long beforeStep(Exchange exchange, String nodeId) {
        int n = asked.computeIfAbsent(nodeId, k -> new AtomicInteger()).incrementAndGet();
        Integer times = failures.get(nodeId);
        if (times != null && n <= times) {
            exchange.setException(new IllegalArgumentException("Failed by the step hook"));
        }
        return delays.getOrDefault(nodeId, StepHook.PROCEED);
    }

    @Test
    void testFailureWhenTheErrorHandlerCanRedeliver() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        failures.put("flaky-step", 2);

        Object out = template.requestBody("direct:flaky", "Hello World");
        assertEquals("Hello World", out);

        // the same as for a processor that fails twice (see ManagedRedeliverTest)
        ObjectName on = getCamelObjectName(TYPE_PROCESSOR, "flaky-step");
        assertEquals(2L, mbeanServer.getAttribute(on, "ExchangesFailed"));
        assertEquals(1L, mbeanServer.getAttribute(on, "ExchangesCompleted"));
        assertEquals(2L, mbeanServer.getAttribute(on, "Redeliveries"));
        on = getCamelObjectName(TYPE_ROUTE, "flaky");
        assertEquals(1L, mbeanServer.getAttribute(on, "ExchangesCompleted"));
        assertEquals(1L, mbeanServer.getAttribute(on, "Redeliveries"));
        assertEquals(3, asked.get("flaky-step").get());
    }

    @Test
    void testFailureWhenTheErrorHandlerCannotRedeliver() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        failures.put("once-step", 1);

        Exchange out = template.send("direct:once", e -> e.getMessage().setBody("Hello World"));
        assertInstanceOf(IllegalArgumentException.class, out.getException());

        ObjectName on = getCamelObjectName(TYPE_PROCESSOR, "once-step");
        assertEquals(1L, mbeanServer.getAttribute(on, "ExchangesFailed"));
        assertEquals(1L, mbeanServer.getAttribute(on, "ExchangesTotal"));
        on = getCamelObjectName(TYPE_ROUTE, "once");
        assertEquals(1L, mbeanServer.getAttribute(on, "ExchangesFailed"));
        assertEquals(1, asked.get("once-step").get());
    }

    @Test
    void testDelayIsProcessingTimeOfTheStep() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        delays.put("flaky-step", 50L);
        delays.put("once-step", 50L);

        template.requestBody("direct:flaky", "Hello World");
        template.requestBody("direct:once", "Hello World");

        for (String id : new String[] { "flaky-step", "once-step" }) {
            ObjectName on = getCamelObjectName(TYPE_PROCESSOR, id);
            assertEquals(1L, mbeanServer.getAttribute(on, "ExchangesCompleted"), id);
            long max = (Long) mbeanServer.getAttribute(on, "MaxProcessingTime");
            assertTrue(max >= 50, id + " MaxProcessingTime " + max);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:flaky").routeId("flaky")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0).logStackTrace(false))
                        .to("mock:flaky").id("flaky-step");

                from("direct:once").routeId("once")
                        .errorHandler(defaultErrorHandler().logStackTrace(false))
                        .to("mock:once").id("once-step");
            }
        };
    }
}
