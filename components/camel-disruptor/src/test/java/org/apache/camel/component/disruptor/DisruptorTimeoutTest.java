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
package org.apache.camel.component.disruptor;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class DisruptorTimeoutTest extends CamelTestSupport {
    private int timeout = 100;

    @Test
    void testDisruptorNoTimeout() throws Exception {
        final MockEndpoint result = getMockEndpoint("mock:result");
        result.setExpectedMessageCount(1);
        final Future<String> out = template.asyncRequestBody("disruptor:foo", "World", String.class);
        assertEquals("Bye World", out.get());
        result.await(1, TimeUnit.SECONDS);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testDisruptorTimeout() throws Exception {
        final MockEndpoint result = getMockEndpoint("mock:result");
        result.setExpectedMessageCount(0);

        final Future<String> out = template
                .asyncRequestBody("disruptor:foo?timeout=" + timeout, "World", String.class);
        try {
            out.get();
            fail("Should have thrown an exception");
        } catch (ExecutionException e) {
            assertIsInstanceOf(CamelExecutionException.class, e.getCause());
            assertIsInstanceOf(ExchangeTimedOutException.class, e.getCause().getCause());

            final DisruptorEndpoint de = (DisruptorEndpoint) context.getRoute("disruptor").getEndpoint();
            assertNotNull(de, "Consumer endpoint cannot be null");
            //we can't remove the exchange from a Disruptor once it is published, but it should never reach the
            //mock:result endpoint because it should be filtered out by the DisruptorConsumer
            result.await(1, TimeUnit.SECONDS);
            MockEndpoint.assertIsSatisfied(context);
        }
    }

    @Test
    void testDisruptorTimeoutWithStoppedRoute() throws Exception {
        context.getRouteController().stopRoute("disruptor");
        timeout = 500;
        testDisruptorTimeout();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("disruptor:foo").routeId("disruptor").to("mock:before").delay(250)
                        .transform(body().prepend("Bye ")).to("mock:result");
            }
        };
    }
}
