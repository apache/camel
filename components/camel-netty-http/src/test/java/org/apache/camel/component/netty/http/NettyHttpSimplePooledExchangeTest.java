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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.apache.camel.impl.engine.PooledProcessorExchangeFactory;
import org.apache.camel.spi.PooledObjectFactory;
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
        ExtendedCamelContext ecc = camelContext.getCamelContextExtension();

        ecc.setExchangeFactory(new PooledExchangeFactory());
        ecc.setProcessorExchangeFactory(new PooledProcessorExchangeFactory());
        ecc.getExchangeFactory().setStatisticsEnabled(true);
        ecc.getProcessorExchangeFactory().setStatisticsEnabled(true);

        return camelContext;
    }

    @Order(1)
    @Test
    public void testOne() throws Exception {
        Assumptions.assumeTrue(context.isStarted());

        getMockEndpoint("mock:input").expectedBodiesReceived("World");

        String out = template.requestBody("netty-http:http://localhost:{{port}}/pooled", "World", String.class);
        assertEquals("Bye World", out);

        MockEndpoint.assertIsSatisfied(context);

        Awaitility.waitAtMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            PooledObjectFactory.Statistics stat
                    = context.getCamelContextExtension().getExchangeFactoryManager().getStatistics();
            assertEquals(1, stat.getCreatedCounter());
            assertEquals(0, stat.getAcquiredCounter());
            assertEquals(1, stat.getReleasedCounter());
            assertEquals(0, stat.getDiscardedCounter());
        });
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

        MockEndpoint.assertIsSatisfied(context);

        Awaitility.waitAtMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            PooledObjectFactory.Statistics stat
                    = context.getCamelContextExtension().getExchangeFactoryManager().getStatistics();
            assertEquals(1, stat.getCreatedCounter());
            assertEquals(2, stat.getAcquiredCounter());
            assertEquals(3, stat.getReleasedCounter());
            assertEquals(0, stat.getDiscardedCounter());
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://0.0.0.0:{{port}}/pooled")
                        .convertBodyTo(String.class)
                        .to("mock:input")
                        .transform().simple("Bye ${body}");
            }
        };
    }

}
