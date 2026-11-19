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
package org.apache.camel.coap;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.californium.core.CoapResource;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An observe consumer that stops must release the endpoint of its client (its bound socket and threads), once the
 * cancel of its observation went out.
 */
class CoAPObserverStopDestroysEndpointTest extends CoAPTestSupport {

    @Test
    void routeRestartsReleaseTheEndpointsOfTheStoppedObservers() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:observed");
        // generous waits: a CoAP request that is lost is sent again only after the ACK timeout (2 to 3 seconds, doubled
        // at each retransmission)
        await().atMost(30, TimeUnit.SECONDS).until(() -> mock.getReceivedCounter() >= 1 && observerCount() == 1);
        Set<Thread> before = Thread.getAllStackTraces().keySet();

        for (int i = 0; i < 5; i++) {
            context.getRouteController().stopRoute("observer");
            await().atMost(30, TimeUnit.SECONDS).until(() -> observerCount() == 0);
            mock.reset();
            context.getRouteController().startRoute("observer");
            await().atMost(30, TimeUnit.SECONDS).until(() -> mock.getReceivedCounter() >= 1 && observerCount() == 1);
        }
        context.getRouteController().stopRoute("observer");

        // the cancel of the observation still reaches the server
        await().atMost(30, TimeUnit.SECONDS).until(() -> observerCount() == 0);
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            long receivers = Thread.getAllStackTraces().keySet().stream()
                    .filter(t -> !before.contains(t) && t.isAlive() && t.getName().startsWith("UDP-Receiver"))
                    .count();
            assertEquals(0, receivers, "UDP receiver threads of stopped CoAP observers still alive after 5 restarts");
        });
    }

    private int observerCount() {
        CoAPComponent component = context.getComponent("coap", CoAPComponent.class);
        CoapResource resource = (CoapResource) component.servers.get(PORT.getPort()).getRoot().getChild("TestResource");
        return resource.getObserverCount();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("coap://localhost:%d/TestResource?observable=true", PORT.getPort()).setBody(constant("Hello"));

                fromF("coap://localhost:%d/TestResource?observe=true", PORT.getPort()).routeId("observer")
                        .to("mock:observed");
            }
        };
    }
}
