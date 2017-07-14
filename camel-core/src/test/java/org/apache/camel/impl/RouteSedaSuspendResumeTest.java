/**
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
import org.apache.camel.Route;
import org.apache.camel.StatefulService;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.seda.SedaEndpoint;

import static org.awaitility.Awaitility.await;

/**
 * @version 
 */
public class RouteSedaSuspendResumeTest extends ContextTestSupport {

    public void testSuspendResume() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A");

        template.sendBody("seda:foo", "A");
        
        assertMockEndpointsSatisfied();

        log.info("Suspending");

        // now suspend and dont expect a message to be routed
        resetMocks();
        mock.expectedMessageCount(0);
        context.suspendRoute("foo");

        assertEquals("Suspended", context.getRouteStatus("foo").name());
        Route route = context.getRoute("foo");
        if (route instanceof StatefulService) {
            assertEquals("Suspended", ((StatefulService) route).getStatus().name());
        }

        // need to give seda consumer thread time to idle
        await().atMost(1, TimeUnit.SECONDS).until(() -> context.getEndpoint("seda:foo", SedaEndpoint.class).getQueue().size() == 0);

        template.sendBody("seda:foo", "B");

        mock.assertIsSatisfied(100);

        log.info("Resuming");

        // now resume and expect the previous message to be routed
        resetMocks();
        mock.expectedBodiesReceived("B");
        context.resumeRoute("foo");
        assertMockEndpointsSatisfied();

        assertEquals("Started", context.getRouteStatus("foo").name());
        route = context.getRoute("foo");
        if (route instanceof StatefulService) {
            assertEquals("Started", ((StatefulService) route).getStatus().name());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo").routeId("foo").to("log:foo").to("mock:result");
            }
        };
    }
}
