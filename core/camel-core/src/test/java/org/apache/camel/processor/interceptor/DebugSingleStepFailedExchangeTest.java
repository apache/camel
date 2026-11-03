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
package org.apache.camel.processor.interceptor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.debugger.DefaultDebugger;
import org.apache.camel.support.BreakpointSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A single-stepped exchange that fails must end its single step, as one that completes does. The debugger single-steps
 * at most 100 exchanges at the same time, so otherwise it refuses to single-step any exchange after 100 failed.
 */
public class DebugSingleStepFailedExchangeTest extends ContextTestSupport {

    // the number of exchanges DefaultDebugger single-steps at the same time
    private static final int MAX_CONCURRENT_SINGLE_STEPS = 100;

    @Test
    public void testFailedExchangeStartedInTheRouteEndsItsSingleStep() {
        // as the backlog debugger does when it steps a suspended exchange
        for (int i = 0; i <= MAX_CONCURRENT_SINGLE_STEPS; i++) {
            Exchange failed = template.send("direct:start", e -> e.getIn().setBody("Kaboom"));
            assertNotNull(failed.getException(), "the exchange should fail");
            assertEquals(Boolean.TRUE, failed.getProperty("started"),
                    "single stepping exchange " + (i + 1) + " should be accepted after " + i + " failed");
        }
    }

    @Test
    public void testFailedExchangeOfSingleStepBreakpointEndsItsSingleStep() {
        context.getDebugger().addSingleStepBreakpoint(new BreakpointSupport() {
        });

        for (int i = 0; i < MAX_CONCURRENT_SINGLE_STEPS; i++) {
            Exchange failed = template.send("direct:fail", e -> e.getIn().setBody("Kaboom"));
            assertNotNull(failed.getException(), "the exchange should fail");
        }

        assertTrue(context.getDebugger().startSingleStepExchange("next", new BreakpointSupport() {
        }), "single stepping should be accepted after " + MAX_CONCURRENT_SINGLE_STEPS + " single-stepped exchanges failed");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setDebugging(true);
                context.setDebugger(new DefaultDebugger());

                from("direct:start")
                        .process(e -> e.setProperty("started", e.getContext().getDebugger()
                                .startSingleStepExchange(e.getExchangeId(), new BreakpointSupport() {
                                })))
                        .throwException(new IllegalArgumentException("Forced"));

                from("direct:fail")
                        .throwException(new IllegalArgumentException("Forced"));
            }
        };
    }
}
