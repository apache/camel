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
package org.apache.camel.component.netty.http;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NettyHttpSimplePooledExchangeTest extends BaseNettyTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.adapt(ExtendedCamelContext.class).setExchangeFactory(new PooledExchangeFactory());
        return camelContext;
    }

    @Order(1)
    @Test
    public void testOne() throws Exception {
        Assumptions.assumeTrue(context.isStarted());

        getMockEndpoint("mock:input").expectedBodiesReceived("World");

        String out = template.requestBody("netty-http:http://localhost:{{port}}/pooled", "World", String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    @Order(2)
    @Test
    public void testThree() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("World", "Camel", "Earth");

        String out = template.requestBody("netty-http:http://localhost:{{port}}/pooled", "World", String.class);
        assertEquals("Bye World", out);

        out = template.requestBody("netty-http:http://localhost:{{port}}/pooled", "Camel", String.class);
        assertEquals("Bye Camel", out);

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(
                () -> {
                    String reqOut = template.requestBody("netty-http:http://localhost:{{port}}/pooled", "Earth", String.class);
                    assertEquals("Bye Earth", reqOut);
                });

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty-http:http://0.0.0.0:{{port}}/pooled")
                        .convertBodyTo(String.class)
                        .to("mock:input")
                        .transform().simple("Bye ${body}");
            }
        };
    }

}
