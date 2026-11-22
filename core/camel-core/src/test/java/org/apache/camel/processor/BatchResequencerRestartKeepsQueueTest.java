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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.RouteStoppingException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The batch resequencer completes an exchange as soon as it is queued for the current batch. The exchanges still
 * waiting for the batch to complete (size or timeout) must be delivered after the route is stopped and started again,
 * as the exchanges of a batch being delivered are (BatchResequencerRestartTest).
 */
class BatchResequencerRestartKeepsQueueTest extends ContextTestSupport {

    @Test
    void testQueuedExchangesAreDeliveredAfterRestart() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("1", "2");

        // both are accepted, and the batch (size 10, timeout 5 seconds) is not complete yet
        assertNull(send("2", 2).getException());
        assertNull(send("1", 1).getException());

        // stop and start the route before the batch timeout
        context.getRouteController().stopRoute("batch");
        context.getRouteController().startRoute("batch");

        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
    }

    @Test
    void testQueuedExchangesAreDeliveredWithLaterOnesAfterTwoRestarts() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("1", "2", "3", "4");

        assertNull(send("4", 4).getException());
        assertNull(send("2", 2).getException());
        context.getRouteController().stopRoute("batch");
        context.getRouteController().startRoute("batch");
        context.getRouteController().stopRoute("batch");
        context.getRouteController().startRoute("batch");
        assertNull(send("3", 3).getException());
        assertNull(send("1", 1).getException());

        // one batch, in order, with the exchanges queued before and after the restarts
        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
    }

    @Test
    void testStoppedResequencerRejectsWithRouteStoppingException() throws Exception {
        Resequencer resequencer = new Resequencer(context, e -> {
        }, ExpressionBuilder.headerExpression("seq"));
        resequencer.setId("resequence1");
        ServiceHelper.startService(resequencer);
        ServiceHelper.stopService(resequencer);
        try {
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setHeader("seq", 1);
            AtomicBoolean done = new AtomicBoolean();

            assertTrue(resequencer.process(exchange, done::set));

            assertTrue(done.get());
            RouteStoppingException e = assertInstanceOf(RouteStoppingException.class, exchange.getException());
            assertEquals("Resequencer is stopped: " + resequencer, e.getMessage());
        } finally {
            ServiceHelper.stopAndShutdownService(resequencer);
        }
    }

    private Exchange send(String body, int seq) {
        return template.send("direct:start", e -> {
            e.getMessage().setBody(body);
            e.getMessage().setHeader("seq", seq);
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("batch")
                        .resequence(header("seq")).batch().size(10)
                        // the sends and restarts of a test must fit in one batch timeout, with margin for a slow CI
                        .timeout(5000)
                        .to("mock:result");
            }
        };
    }
}
