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
package org.apache.camel.issues;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.Test;

public class GertJBIIssueTest extends ContextTestSupport {

    private static Exception cause;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testSimulateJBIEndpointFail() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dlc").maximumRedeliveries(0));

                from("direct:start").threads(2).to("mock:done").throwException(new IllegalArgumentException("Forced"));

            }
        });
        context.start();

        getMockEndpoint("mock:done").expectedMessageCount(1);
        getMockEndpoint("mock:dlc").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSimulateJBIEndpointNotExistOnCompletion() throws Exception {
        cause = null;

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").threads(2).to("mock:done").throwException(new IllegalArgumentException("Forced"));
            }
        });
        context.start();

        getMockEndpoint("mock:done").expectedMessageCount(1);

        final CountDownLatch latch = new CountDownLatch(1);

        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.adapt(ExtendedExchange.class).addOnCompletion(new SynchronizationAdapter() {
                    @Override
                    public void onDone(Exchange exchange) {
                        cause = exchange.getException();
                        latch.countDown();
                    }
                });
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        assertNotNull("Should have failed", cause);
        assertIsInstanceOf(IllegalArgumentException.class, cause);
        assertEquals("Forced", cause.getMessage());
    }

}
