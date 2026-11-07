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
package org.apache.camel.processor;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A loop with breakOnShutdown must loop again after its route was stopped and started, or suspended and resumed.
 */
class LoopBreakOnShutdownRestartTest extends ContextTestSupport {

    @Test
    void testLoopAfterRouteStopAndStart() throws Exception {
        sendAndExpectThreeIterations("a");

        context.getRouteController().stopRoute("loop");
        context.getRouteController().startRoute("loop");

        sendAndExpectThreeIterations("b");
    }

    @Test
    void testLoopAfterRouteSuspendAndResume() throws Exception {
        sendAndExpectThreeIterations("a");

        context.getRouteController().suspendRoute("loop");
        context.getRouteController().resumeRoute("loop");

        sendAndExpectThreeIterations("b");
    }

    @Test
    void testLoopAfterCamelContextSuspendAndResume() throws Exception {
        sendAndExpectThreeIterations("a");

        context.suspend();
        context.resume();

        sendAndExpectThreeIterations("b");
    }

    @Test
    void testLoopBreaksOnShutdownAfterRestart() throws Exception {
        context.getRouteController().stopRoute("slow");
        context.getRouteController().startRoute("slow");

        MockEndpoint slow = getMockEndpoint("mock:slow");
        template.sendBody("seda:slow", "c");
        await().atMost(10, TimeUnit.SECONDS).until(() -> slow.getReceivedCounter() >= 1);

        context.getRouteController().stopRoute("slow");

        int received = slow.getReceivedCounter();
        assertTrue(received < 100, "the loop did not break on shutdown: " + received + " iterations");
    }

    private void sendAndExpectThreeIterations(String body) throws Exception {
        MockEndpoint iteration = getMockEndpoint("mock:iteration");
        MockEndpoint result = getMockEndpoint("mock:result");
        iteration.reset();
        result.reset();
        iteration.expectedBodiesReceived(body, body, body);
        result.expectedBodiesReceived(body);

        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("loop")
                        .loop(3).breakOnShutdown()
                            .to("mock:iteration")
                        .end()
                        .to("mock:result");

                from("seda:slow").routeId("slow")
                        .loop(100).breakOnShutdown()
                            .delay(20)
                            .to("mock:slow")
                        .end();
            }
        };
    }
}
