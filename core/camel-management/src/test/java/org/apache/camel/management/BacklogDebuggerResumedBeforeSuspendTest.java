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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A breakpoint that is resumed while the exchange that matched it has not suspended yet, and another exchange that
 * suspends at the same node meanwhile: each exchange waits for its own resume, and the debugger shows the exchange that
 * is suspended.
 */
@DisabledOnOs(OS.AIX)
public class BacklogDebuggerResumedBeforeSuspendTest extends ManagementTestSupport {

    private final CountDownLatch dumping = new CountDownLatch(1);
    private final CountDownLatch dumped = new CountDownLatch(1);
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
        dumped.countDown();
        if (debugger != null) {
            debugger.resumeAll();
        }
    }

    @Test
    public void testResumedBeforeTheExchangeSuspended() throws Exception {
        debugger.addBreakpoint("bar");
        MockEndpoint mock = getMockEndpoint("mock:result");

        // the first exchange matches the breakpoint, and is held while the debugger dumps its message
        template.sendBody("seda:start", new SlowBody());
        assertTrue(dumping.await(10, TimeUnit.SECONDS));
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(Set.of("bar"), debugger.getSuspendedBreakpointNodeIds()));

        // the user resumes the breakpoint, and a second exchange suspends at it
        debugger.resumeBreakpoint("bar");
        template.sendBody("seda:start", "Second");
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(String.valueOf(debugger.dumpTracedMessagesAsXml("bar")).contains("Second")));
        String second = debugger.getSuspendedExchangeIds().iterator().next();

        // the first exchange was resumed: it continues and does not wait for the resume of the second
        mock.expectedBodiesReceived("Slow");
        dumped.countDown();
        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, mock);

        // the debugger still shows the second exchange, suspended at the breakpoint
        assertEquals(Set.of(second), debugger.getSuspendedExchangeIds());
        String dump = debugger.dumpTracedMessagesAsXml("bar");
        assertTrue(dump.contains(second), dump);
        assertTrue(dump.contains("Second"), dump);

        resetMocks();
        mock.expectedBodiesReceived("Second");
        debugger.resumeBreakpoint("bar");
        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, mock);
    }

    @Test
    public void testTimeoutAfterTheBodyWasChangedLeavesNoMessage() throws Exception {
        debugger.setFallbackTimeout(2);
        debugger.addBreakpoint("bar");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Changed");

        template.sendBody("seda:start", "Hello");
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(Set.of("bar"), debugger.getSuspendedBreakpointNodeIds()));
        // the debugger replaces the message of the node when the body is changed
        debugger.setMessageBodyOnBreakpoint("bar", "Changed");

        // the breakpoint times out: the exchange continues, and nothing is left at the node
        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, mock);
        assertEquals(Set.of(), debugger.getSuspendedBreakpointNodeIds());
        assertNull(debugger.dumpTracedMessagesAsXml("bar"));
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

    /**
     * A body that holds the dump of its message by the debugger at the breakpoint (its conversion to a String).
     */
    private final class SlowBody {
        private final AtomicBoolean first = new AtomicBoolean(true);

        @Override
        public String toString() {
            // only once this exchange matched the breakpoint at bar (the debugger dumps the message there)
            if (debugger.getSuspendedBreakpointNodeIds().contains("bar") && first.compareAndSet(true, false)) {
                dumping.countDown();
                try {
                    dumped.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return "Slow";
        }
    }
}
