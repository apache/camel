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

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.BacklogDebugger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An exchange that is single-stepped into a node where another exchange is suspended by a node breakpoint does not
 * replace that exchange: it suspends at the node once the other exchange continues.
 */
@DisabledOnOs(OS.AIX)
public class BacklogDebuggerStepIntoSuspendedNodeTest extends ManagementTestSupport {

    private BacklogDebugger debugger;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        debugger = context.hasService(BacklogDebugger.class);
        debugger.enableDebugger();
        // the exchanges of a failed test continue after this, not after the default 300 seconds
        debugger.setFallbackTimeout(20);
    }

    @AfterEach
    void resumeAll() {
        if (debugger != null) {
            debugger.resumeAll();
        }
    }

    @Test
    public void testStepIntoNodeWithSuspendedExchange() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Second", "First");

        // the second exchange is suspended at bar by its node breakpoint
        debugger.addBreakpoint("bar");
        template.sendBody("seda:start", "Second");
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(Set.of("bar"), debugger.getSuspendedBreakpointNodeIds()));
        String second = debugger.getSuspendedExchangeIds().iterator().next();

        // the first exchange is suspended at foo, and is single-stepped from there into bar
        debugger.addBreakpoint("foo");
        template.sendBody("seda:start", "First");
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(Set.of("foo", "bar"), debugger.getSuspendedBreakpointNodeIds()));
        String first = debugger.getSuspendedExchangeIds().stream().filter(id -> !id.equals(second)).findFirst()
                .orElseThrow();
        long counter = debugger.getDebugCounter();
        debugger.stepBreakpoint("foo");

        // the step of the first exchange reaches bar (it counts a message there)
        await().atMost(5, TimeUnit.SECONDS).until(() -> debugger.getDebugCounter() > counter);

        // the debugger still shows the second exchange suspended at bar
        await().during(500, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(Set.of(second), debugger.getSuspendedExchangeIds());
            String dump = debugger.dumpTracedMessagesAsXml("bar");
            assertTrue(dump.contains(second), dump);
            assertTrue(dump.contains("Second"), dump);
        });

        // a step continues the second exchange, and then the first exchange is suspended at bar
        debugger.step();
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(Set.of(first), debugger.getSuspendedExchangeIds());
            String dump = debugger.dumpTracedMessagesAsXml("bar");
            assertTrue(dump != null && dump.contains(first), dump);
        });

        // the first exchange is resumed too, and both exchanges complete
        debugger.resumeBreakpoint("bar");
        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, mock);
    }

    @Test
    public void testResumeWhileTheStepWaitsForTheNode() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Second", "First");

        debugger.addBreakpoint("bar");
        template.sendBody("seda:start", "Second");
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(Set.of("bar"), debugger.getSuspendedBreakpointNodeIds()));
        String second = debugger.getSuspendedExchangeIds().iterator().next();

        debugger.addBreakpoint("foo");
        template.sendBody("seda:start", "First");
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(Set.of("foo", "bar"), debugger.getSuspendedBreakpointNodeIds()));
        long counter = debugger.getDebugCounter();
        debugger.stepBreakpoint("foo");
        await().atMost(5, TimeUnit.SECONDS).until(() -> debugger.getDebugCounter() > counter);
        await().during(500, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(Set.of(second), debugger.getSuspendedExchangeIds()));

        // a resume (not a step) continues the second exchange and ends the single step of the first exchange, which
        // then continues without suspending at bar
        debugger.resumeBreakpoint("bar");
        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, mock);
        assertEquals(Set.of(), debugger.getSuspendedExchangeIds());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setDebugging(true);

                from("seda:start?concurrentConsumers=2")
                        .setProperty("num", constant(1)).id("foo")
                        .setHeader("node", constant("bar")).id("bar")
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }
}
