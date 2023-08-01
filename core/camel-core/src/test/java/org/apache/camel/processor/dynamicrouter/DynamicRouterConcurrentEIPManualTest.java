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
package org.apache.camel.processor.dynamicrouter;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;

/**
 * {@link DynamicRouterConcurrentPOJOManualTest}
 */
@Disabled("Manual test together with DynamicRouterConcurrentPOJOTest")
public class DynamicRouterConcurrentEIPManualTest extends ContextTestSupport {

    private static final int COUNT = 100;

    @RepeatedTest(100)
    public void testConcurrentDynamicRouter() throws Exception {
        final MockEndpoint mockA = getMockEndpoint("mock:a");
        mockA.expectedMessageCount(COUNT);
        final MockEndpoint mockB = getMockEndpoint("mock:b");
        mockB.expectedMessageCount(COUNT);

        Thread sendToSedaA = createSedaSenderThread("seda:a", context.createProducerTemplate());
        Thread sendToSedaB = createSedaSenderThread("seda:b", context.createProducerTemplate());

        sendToSedaA.start();
        sendToSedaB.start();

        sendToSedaA.join(10000);
        sendToSedaB.join(10000);

        /*
         * Awaiting the sum of the two mocks to be 200 makes demonstrating CAMEL-19487
         * a bit faster: the problem is that sometimes messages for mock:a land in mock:b or vice versa
         * but the sum is always 200
         */
        Awaitility.waitAtMost(10, TimeUnit.SECONDS).until(() -> mockA.getReceivedCounter() + mockB.getReceivedCounter() == 200);

        /* Now that all messages were delivered, let's make sure that messages for mock:a did not land in mock:b or vice versa */
        Assertions.assertThat(mockA.getReceivedExchanges())
                .map(Exchange::getMessage)
                .map(m -> m.getBody(String.class))
                .filteredOn(body -> body.contains("Message from seda:b"))
                .as(
                        "Expected mock:a to contain only messages from seda:a, but there were also messages from seda:b")
                .isEmpty();

        Assertions.assertThat(mockB.getReceivedExchanges())
                .map(Exchange::getMessage)
                .map(m -> m.getBody(String.class))
                .filteredOn(body -> body.contains("Message from seda:a"))
                .as(
                        "Expected mock:b to contain only messages from seda:b, but there were also messages from seda:a")
                .isEmpty();

        Assertions.assertThat(mockA.getReceivedCounter()).isEqualTo(100);
        Assertions.assertThat(mockB.getReceivedCounter()).isEqualTo(100);

    }

    private Thread createSedaSenderThread(final String seda, final ProducerTemplate perThreadtemplate) {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < COUNT; i++) {
                    perThreadtemplate.sendBody(seda, "Message from " + seda + " " + i);
                }
            }
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                MyDynamicRouterPojo a = new MyDynamicRouterPojo("mock:a");
                MyDynamicRouterPojo b = new MyDynamicRouterPojo("mock:b");

                from("seda:a")
                        .dynamicRouter(method(a, "route"));

                from("seda:b")
                        .dynamicRouter(method(b, "route"));
            }
        };
    }

    public static class MyDynamicRouterPojo {

        private final String target;

        public MyDynamicRouterPojo(String target) {
            this.target = target;
        }

        public String route(@Header(Exchange.SLIP_ENDPOINT) String previous) {
            if (previous == null) {
                return target;
            } else {
                return null;
            }
        }
    }
}
