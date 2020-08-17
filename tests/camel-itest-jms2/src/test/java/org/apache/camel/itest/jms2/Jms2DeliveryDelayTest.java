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
package org.apache.camel.itest.jms2;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class Jms2DeliveryDelayTest extends BaseJms2TestSupport {

    @Test
    void testInOnlyWithDelay() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        long start = System.currentTimeMillis();
        template.sendBody("jms:topic:foo?deliveryDelay=1000", "Hello World");
        assertMockEndpointsSatisfied();
        assertTrue(System.currentTimeMillis() - start >= 1000, "Should take at least 1000 millis");
    }

    @Test
    void testInOnlyWithoutDelay() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        long start = System.currentTimeMillis();
        template.sendBody("jms:topic:foo", "Hello World");
        assertMockEndpointsSatisfied();
        assertTrue(System.currentTimeMillis() - start < 1000, "Should take less than 1000 millis");
    }

    @Test
    void testInOutWithDelay() {
        long start = System.currentTimeMillis();
        template.requestBody("jms:topic:foo?deliveryDelay=1000", "Hello World");
        assertTrue(System.currentTimeMillis() - start >= 1000, "Should take at least 1000 millis");
    }

    @Test
    void testInOutWithoutDelay() {
        long start = System.currentTimeMillis();
        template.requestBody("jms:topic:foo", "Hello World");
        assertTrue(System.currentTimeMillis() - start < 1000, "Should take less than 1000 millis");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:topic:foo")
                        .to("mock:result");
            }
        };
    }
}
