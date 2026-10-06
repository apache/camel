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
package org.apache.camel.impl.engine;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectConsumerNotAvailableException;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25365: a route stopped while its exchanges wait for a direct consumer, as a dev mode reload that adds the
 * consumer in the same edit does. The shutdown says where they wait, and an interrupted exchange says why it failed.
 */
public class ShutdownWaitingAtTest extends ContextTestSupport {

    @Test
    public void theWaitSaysWhereTheExchangesAre() throws Exception {
        context.getInflightRepository().setInflightBrowseEnabled(true);
        context.getShutdownStrategy().setTimeout(2);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);
        context.getShutdownStrategy().setLogInflightExchangesOnTimeout(false);

        template.sendBody("seda:start", "A");
        await().atMost(5, TimeUnit.SECONDS).until(() -> context.getInflightRepository().size() == 1);

        String at = DefaultShutdownStrategy.waitingAt(context, context.getCamelContextExtension().getRouteStartupOrder());
        assertEquals(". Waiting at: picked-lines/shipment", at);

        context.getRouteController().stopRoute("picked-lines");
    }

    @Test
    public void theInterruptedExchangeSaysWhy() throws Exception {
        AtomicReference<Exception> failure = new AtomicReference<>();
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event instanceof CamelEvent.ExchangeFailedEvent failed) {
                    failure.compareAndSet(null, failed.getExchange().getException());
                }
            }
        });
        context.getShutdownStrategy().setTimeout(1);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);

        template.sendBody("seda:start", "A");
        await().atMost(5, TimeUnit.SECONDS).until(() -> context.getInflightRepository().size() == 1);
        // a consumer thread is blocked waiting for the direct consumer; the forced shutdown interrupts it
        context.getRouteController().stopRoute("picked-lines");

        await().atMost(5, TimeUnit.SECONDS).until(() -> failure.get() != null);
        DirectConsumerNotAvailableException e = assertInstanceOf(DirectConsumerNotAvailableException.class, failure.get());
        assertTrue(e.getMessage().contains("direct://shipment"), e.getMessage());
        assertTrue(e.getMessage().contains("interrupted while waiting for one, as the route is being stopped or reloaded"),
                e.getMessage());
    }

    @Test
    public void nothingWhenTheRepositoryCannotBeBrowsed() {
        assertEquals("",
                DefaultShutdownStrategy.waitingAt(context, context.getCamelContextExtension().getRouteStartupOrder()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:start").routeId("picked-lines")
                        .to("direct:shipment").id("shipment");
            }
        };
    }
}
