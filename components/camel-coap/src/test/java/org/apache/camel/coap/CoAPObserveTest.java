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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapResponse;
import org.junit.jupiter.api.Test;

public class CoAPObserveTest extends CoAPTestSupport {

    @Produce("direct:notify")
    protected ProducerTemplate notify;

    @Produce("mock:coapHandlerResults")
    protected ProducerTemplate coapHandlerResults;

    @Test
    void testServerObservable() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:coapHandlerResults");
        mock.expectedBodiesReceived("Hello 0");

        CoapClient client = createClient("/TestResource");
        client.observe(new CoapHandler() {
            @Override
            public void onLoad(CoapResponse response) {
                coapHandlerResults.sendBody(response.getResponseText());
            }

            @Override
            public void onError() {
                coapHandlerResults.sendBody(null);
            }
        });

        MockEndpoint.assertIsSatisfied(context());
        mock.reset();

        mock.expectedBodiesReceivedInAnyOrder("Hello 1", "Hello 2");

        notify.sendBody(null);
        // send when we have received
        Awaitility.await().until(() -> mock.getReceivedCounter() > 0);
        notify.sendBody(null);

        MockEndpoint.assertIsSatisfied(context());
    }

    @Test
    void testClientAndServerObservable() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:sourceResults");
        mock.expectedBodiesReceived("Hello 0");

        MockEndpoint.assertIsSatisfied(context());
        mock.reset();

        mock.expectedBodiesReceivedInAnyOrder("Hello 1", "Hello 2");

        notify.sendBody(null);
        // send when we have received
        Awaitility.await().until(() -> mock.getReceivedCounter() > 0);
        notify.sendBody(null);

        MockEndpoint.assertIsSatisfied(context());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                AtomicInteger i = new AtomicInteger(0);

                fromF("coap://localhost:%d/TestResource?observable=true", PORT)
                        .log("Received1: ${body}")
                        .process(exchange -> exchange.getMessage().setBody("Hello " + i.get()));

                from("direct:notify")
                        .process(exchange -> i.incrementAndGet())
                        .log("Sending ${body}")
                        .toF("coap://localhost:%d/TestResource?notify=true", PORT);

                fromF("coap://localhost:%d/TestResource?observe=true", PORT)
                        .log("Received2: ${body}")
                        .to("mock:sourceResults");
            }
        };
    }

}
