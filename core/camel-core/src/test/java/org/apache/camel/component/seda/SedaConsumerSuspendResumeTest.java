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
package org.apache.camel.component.seda;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.Test;

import static org.awaitility.Awaitility.await;

/**
 *
 */
public class SedaConsumerSuspendResumeTest extends ContextTestSupport {

    @Test
    public void testSuspendResume() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:bar");
        mock.expectedMessageCount(1);

        template.sendBody("seda:foo", "A");

        mock.assertIsSatisfied();

        assertEquals("Started", context.getRouteController().getRouteStatus("foo").name());
        assertEquals("Started", context.getRouteController().getRouteStatus("bar").name());

        // suspend bar consumer (not the route)
        SedaConsumer consumer = (SedaConsumer)context.getRoute("bar").getConsumer();

        ServiceHelper.suspendService(consumer);
        assertEquals("Suspended", consumer.getStatus().name());

        // send a message to the route but the consumer is suspended
        // so it should not route it
        resetMocks();
        mock.expectedMessageCount(0);

        // wait a bit to ensure consumer is suspended, as it could be in a poll
        // mode where
        // it would poll and route (there is a little slack (up till 1 sec)
        // before suspension is empowered)
        await().atMost(1, TimeUnit.SECONDS)
            .until(() -> context.getEndpoint("seda:foo", SedaEndpoint.class).getQueue().size() == 0 && context.getEndpoint("seda:bar", SedaEndpoint.class).getQueue().size() == 0);

        template.sendBody("seda:foo", "B");
        // wait a little to ensure seda consumer thread would have tried to poll
        // otherwise
        mock.assertIsSatisfied(50);

        // resume consumer
        resetMocks();
        mock.expectedMessageCount(1);

        // resume bar consumer (not the route)
        ServiceHelper.resumeService(consumer);
        assertEquals("Started", consumer.getStatus().name());

        // the message should be routed now
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo").routeId("foo").to("seda:bar");

                from("seda:bar").routeId("bar").to("mock:bar");
            }
        };
    }
}
