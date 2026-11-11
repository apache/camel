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
package org.apache.camel.processor.aggregator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * forceCompletionOnStop and completeAllOnStop complete the groups when the route or the CamelContext is stopped. A
 * suspend is not a stop: the groups are kept and go on aggregating after the resume, and a later stop completes them.
 */
class AggregateSuspendResumeTest extends ContextTestSupport {

    @Test
    void testForceCompletionOnStopKeepsGroupsOnRouteSuspend() throws Exception {
        sendTwo("direct:force");

        context.getRouteController().suspendRoute("force");
        assertEquals(ServiceStatus.Suspended, context.getRouteController().getRouteStatus("force"));
        assertEquals(0, getMockEndpoint("mock:force").getReceivedCounter(), "a suspend completed the group");

        context.getRouteController().resumeRoute("force");
        expectGroupCompletedAfterResume("direct:force", "mock:force");
    }

    @Test
    void testCompleteAllOnStopKeepsGroupsOnRouteSuspend() throws Exception {
        sendTwo("direct:all");

        // the shutdown timeout of the tests is 10 seconds
        StopWatch watch = new StopWatch();
        context.getRouteController().suspendRoute("all");
        assertTrue(watch.taken() < 5000, "the suspend waited for the groups to complete: " + watch.taken() + " ms");
        assertEquals(ServiceStatus.Suspended, context.getRouteController().getRouteStatus("all"));
        assertEquals(0, getMockEndpoint("mock:all").getReceivedCounter(), "a suspend completed the group");

        context.getRouteController().resumeRoute("all");
        expectGroupCompletedAfterResume("direct:all", "mock:all");
    }

    @Test
    void testForceCompletionOnStopKeepsGroupsOnCamelContextSuspend() throws Exception {
        // control: a CamelContext suspend did not complete the groups before either (the context is not started)
        sendTwo("direct:force");

        context.suspend();
        assertEquals(0, getMockEndpoint("mock:force").getReceivedCounter(), "a suspend completed the group");

        context.resume();
        expectGroupCompletedAfterResume("direct:force", "mock:force");
    }

    @Test
    void testCompleteAllOnStopKeepsGroupsOnCamelContextSuspend() throws Exception {
        sendTwo("direct:all");

        // the shutdown timeout of the tests is 10 seconds
        StopWatch watch = new StopWatch();
        context.suspend();
        assertTrue(watch.taken() < 5000, "the suspend waited for the groups to complete: " + watch.taken() + " ms");
        assertEquals(0, getMockEndpoint("mock:all").getReceivedCounter(), "a suspend completed the group");

        context.resume();
        expectGroupCompletedAfterResume("direct:all", "mock:all");
    }

    @Test
    void testForceCompletionOnStopCompletesKeptGroupsOnStopAfterSuspend() throws Exception {
        expectGroupsCompletedOnStopAfterSuspend("direct:force", "force", "mock:force");
    }

    @Test
    void testCompleteAllOnStopCompletesKeptGroupsOnStopAfterSuspend() throws Exception {
        expectGroupsCompletedOnStopAfterSuspend("direct:all", "all", "mock:all");
    }

    private void expectGroupsCompletedOnStopAfterSuspend(String uri, String routeId, String mockUri) throws Exception {
        MockEndpoint mock = getMockEndpoint(mockUri);
        mock.expectedBodiesReceived("A+B");

        sendTwo(uri);
        context.getRouteController().suspendRoute(routeId);
        assertEquals(0, mock.getReceivedCounter(), "a suspend completed the group");
        context.getRouteController().stopRoute(routeId);

        mock.assertIsSatisfied();
    }

    private void sendTwo(String uri) {
        template.sendBodyAndHeader(uri, "A", "id", "foo");
        template.sendBodyAndHeader(uri, "B", "id", "foo");
    }

    private void expectGroupCompletedAfterResume(String uri, String mockUri) throws Exception {
        MockEndpoint mock = getMockEndpoint(mockUri);
        mock.expectedBodiesReceived("A+B+C");

        template.sendBodyAndHeader(uri, "C", "id", "foo");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:force").routeId("force")
                        .aggregate(header("id"), new BodyInAggregatingStrategy())
                        .completionSize(3).forceCompletionOnStop()
                        .to("mock:force");

                from("direct:all").routeId("all")
                        .aggregate(header("id"), new BodyInAggregatingStrategy())
                        .completionSize(3).completeAllOnStop()
                        .to("mock:all");
            }
        };
    }
}
