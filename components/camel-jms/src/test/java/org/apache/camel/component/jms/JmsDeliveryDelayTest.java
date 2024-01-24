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
package org.apache.camel.component.jms;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * Note: these tests offer only a naive check of the deliveryDelay functionality as they check the
 * test duration. There is no guarantee that the cause for the delay is actually the deliveryDelay
 * feature per se and not, for instance, caused by bug on the message broker or an overloaded scheduler
 * taking a long time to handle this test workload. Nonetheless, it can still be useful for investigating
 * bugs which is why we keep them here.
 */
@Tags({ @Tag("not-parallel") })
public class JmsDeliveryDelayTest extends AbstractPersistentJMSTest {

    private CountDownLatch routeComplete;
    private final StopWatch routeWatch = new StopWatch();

    @Test
    void testInOnlyWithDelay() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        routeWatch.restart();
        template.sendBody("activemq:topic:JmsDeliveryDelayTest?deliveryDelay=1000", "Hello World");
        if (!routeComplete.await(5000, TimeUnit.MILLISECONDS)) {
            fail("Message was not received from Artemis topic for too long");
        }

        MockEndpoint.assertIsSatisfied(context);
        // give some slack
        assertTrue(routeWatch.taken() >= 900, "Should take at least 1000 millis");
    }

    @Test
    void testInOutWithDelay() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        routeWatch.restart();
        var response = template.requestBody("activemq:topic:JmsDeliveryDelayTest?deliveryDelay=1000", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(response, "Hello World");
        // give some slack
        assertTrue(routeWatch.taken() >= 900, "Should take at least 1000 millis");
    }

    @BeforeEach
    public void initLatch() {
        routeComplete = new CountDownLatch(1);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:topic:JmsDeliveryDelayTest")
                        .to("mock:result")
                        .process(exchange -> routeComplete.countDown());
            }
        };
    }
}
