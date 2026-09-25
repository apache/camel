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
package org.apache.camel.processor;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.loadbalancer.QueueLoadBalancer;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WeightedLoadBalanceInvalidRatioTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "true;0,0;At least one distribution ratio must be a positive number",
            "false;0,0;At least one distribution ratio must be a positive number",
            "true;1,-1;Distribution ratio must be zero or a positive number, was: -1",
            "false;1,-1;Distribution ratio must be zero or a positive number, was: -1",
            "true;2147483647,1;The sum of the distribution ratios must not be greater than 2147483647, was: 2147483648",
            "false;2147483647,1;The sum of the distribution ratios must not be greater than 2147483647, was: 2147483648",
            "true;2147483647,2147483647,3,0;The sum of the distribution ratios must not be greater than 2147483647, was: 4294967297" })
    public void testInvalidRatiosRejectedOnStart(boolean roundRobin, String ratios, String message) throws Exception {
        // one endpoint per ratio, so the number of ratios is valid
        String[] uris = new String[ratios.split(",").length];
        for (int i = 0; i < uris.length; i++) {
            uris[i] = "mock:" + i;
        }
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").loadBalance().weighted(roundRobin, ratios).to(uris);
            }
        });

        Exception e = assertThrows(Exception.class, () -> context.start());
        IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause().getCause());
        assertEquals(message, iae.getMessage());
    }

    @Test
    public void testZeroRatioForSomeProcessorsIsAllowed() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").loadBalance().weighted(true, "0,2,1").to("mock:x", "mock:y", "mock:z");
            }
        });
        context.start();

        getMockEndpoint("mock:x").expectedMessageCount(0);
        getMockEndpoint("mock:y").expectedMessageCount(4);
        getMockEndpoint("mock:z").expectedMessageCount(2);

        for (int i = 0; i < 6; i++) {
            template.sendBody("direct:start", "Hello " + i);
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExceptionWhenChoosingProcessorIsSetOnExchange() throws Exception {
        IllegalStateException cause = new IllegalStateException("Cannot choose");
        QueueLoadBalancer lb = new QueueLoadBalancer() {
            @Override
            protected AsyncProcessor chooseProcessor(AsyncProcessor[] processors, Exchange exchange) {
                throw cause;
            }
        };
        lb.addProcessor(new SendProcessor(context.getEndpoint("mock:x")));

        Exchange exchange = new DefaultExchange(context);
        AtomicBoolean done = new AtomicBoolean();
        lb.process(exchange, doneSync -> done.set(true));

        assertTrue(done.get(), "The callback should be called");
        assertSame(cause, exchange.getException());
    }
}
