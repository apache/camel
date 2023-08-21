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
package org.apache.camel.impl;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultCamelContextSuspendResumeRouteTest extends ContextTestSupport {

    @Test
    public void testSuspendResume() throws Exception {
        assertFalse(context.isSuspended());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A");

        template.sendBody("seda:foo", "A");

        assertMockEndpointsSatisfied();

        log.info("Suspending");

        // now suspend and dont expect a message to be routed
        resetMocks();
        mock.expectedMessageCount(0);

        context.suspend();

        // need to give seda consumer thread time to idle
        Awaitility.await().atMost(100, TimeUnit.MILLISECONDS)
                .pollDelay(10, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> template.sendBody("seda:foo", "B"));

        mock.assertIsSatisfied(1000);

        assertTrue(context.isSuspended());
        assertFalse(context.getStatus().isStarted());
        assertTrue(context.getStatus().isSuspended());
        assertFalse(context.getStatus().isStopped());

        log.info("Resuming");

        // now resume and expect the previous message to be routed
        resetMocks();
        mock.expectedBodiesReceived("B");
        context.resume();
        assertMockEndpointsSatisfied();

        assertFalse(context.isSuspended());

        assertTrue(context.getStatus().isStarted());
        assertFalse(context.getStatus().isSuspended());
        assertFalse(context.getStatus().isStopped());

        context.stop();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo").to("log:foo").to("mock:result");
            }
        };
    }
}
