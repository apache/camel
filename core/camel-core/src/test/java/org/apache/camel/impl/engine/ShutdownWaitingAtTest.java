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

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectConsumerNotAvailableException;
import org.apache.camel.component.log.ConsumingAppender;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.RouteStartupOrder;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25365: a route stopped while its exchanges wait for a direct consumer, as a dev mode reload that adds the
 * consumer in the same edit does. The shutdown says where they wait, and an interrupted exchange says why it failed.
 */
public class ShutdownWaitingAtTest extends ContextTestSupport {

    private static final String SHUTDOWN_LOGGER = DefaultShutdownStrategy.class.getName();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext answer = super.createCamelContext();
        // the place of a node includes where it is in the source
        answer.setSourceLocationEnabled(true);
        return answer;
    }

    @AfterEach
    public void removeAppender() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().removeLogger(SHUTDOWN_LOGGER);
        ctx.updateLoggers();
    }

    @Test
    public void theWaitSaysWhereTheExchangesAre() throws Exception {
        context.getInflightRepository().setInflightBrowseEnabled(true);
        context.getShutdownStrategy().setTimeout(2);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);
        context.getShutdownStrategy().setLogInflightExchangesOnTimeout(false);

        template.sendBody("seda:start", "A");
        awaitAt("shipment");

        String at = DefaultShutdownStrategy.waitingAt(context, context.getCamelContextExtension().getRouteStartupOrder());
        assertTrue(at.startsWith(". Waiting at: picked-lines/shipment ("), at);

        context.getRouteController().stopRoute("picked-lines");
    }

    @Test
    public void theInterruptedExchangeSaysWhy() throws Exception {
        AtomicReference<Exchange> failure = new AtomicReference<>();
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event instanceof CamelEvent.ExchangeFailedEvent failed) {
                    failure.compareAndSet(null, failed.getExchange());
                }
            }
        });
        context.getInflightRepository().setInflightBrowseEnabled(true);
        context.getShutdownStrategy().setTimeout(1);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);

        template.sendBody("seda:start", "A");
        awaitAt("shipment");
        // a consumer thread is blocked waiting for the direct consumer; the forced shutdown interrupts it
        context.getRouteController().stopRoute("picked-lines");

        await().atMost(5, TimeUnit.SECONDS).until(() -> failure.get() != null);
        Exchange exchange = failure.get();
        DirectConsumerNotAvailableException e
                = assertInstanceOf(DirectConsumerNotAvailableException.class, exchange.getException());
        assertTrue(e.getMessage().contains("direct://shipment"), e.getMessage());
        assertTrue(e.getMessage().contains("interrupted while waiting for one, as the route is being stopped or reloaded"),
                e.getMessage());
        // still an interruption: onException(InterruptedException.class) matches through the cause, and the error
        // handler stops routing instead of handling a failure
        assertInstanceOf(InterruptedException.class, e.getCause());
        assertSame(e.getCause(), exchange.getException(InterruptedException.class));
        assertTrue(exchange.getExchangeExtension().isInterrupted());
    }

    @Test
    public void theShutdownLogSaysWhereTheExchangesAre() throws Exception {
        Queue<String> messages = new ConcurrentLinkedQueue<>();
        ConsumingAppender.newAppender(SHUTDOWN_LOGGER, "ShutdownWaitingAtTest", Level.INFO,
                event -> messages.add(event.getMessage().getFormattedMessage()));
        context.getInflightRepository().setInflightBrowseEnabled(true);
        context.getShutdownStrategy().setTimeout(2);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);
        context.getShutdownStrategy().setLogInflightExchangesOnTimeout(false);

        template.sendBody("seda:start", "A");
        awaitAt("shipment");
        context.getRouteController().stopRoute("picked-lines");

        assertTrue(messages.stream().anyMatch(m -> m.startsWith("Waiting as there are still 1 inflight")
                && m.contains(". Waiting at: picked-lines/shipment (")), messages.toString());
    }

    @Test
    public void theVerboseListingLeavesThePlaceOut() throws Exception {
        Queue<String> messages = new ConcurrentLinkedQueue<>();
        ConsumingAppender.newAppender(SHUTDOWN_LOGGER, "ShutdownWaitingAtTest", Level.INFO,
                event -> messages.add(event.getMessage().getFormattedMessage()));
        context.getInflightRepository().setInflightBrowseEnabled(true);
        context.getShutdownStrategy().setTimeout(2);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);
        // the default: the inflight exchanges are listed in full, so the one-line place is not added

        template.sendBody("seda:start", "A");
        awaitAt("shipment");
        context.getRouteController().stopRoute("picked-lines");

        assertTrue(messages.stream().anyMatch(m -> m.startsWith("Waiting as there are still 1 inflight")),
                messages.toString());
        assertTrue(messages.stream().noneMatch(m -> m.contains("Waiting at:")), messages.toString());
    }

    @Test
    public void anExchangeThatCameInThroughDirectIsFoundInTheStoppedRoute() throws Exception {
        context.getInflightRepository().setInflightBrowseEnabled(true);
        context.getShutdownStrategy().setTimeout(1);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);

        // created by the caller route, now waiting in the sub route
        template.sendBody("seda:caller", "A");
        awaitAt("deep");

        String at = DefaultShutdownStrategy.waitingAt(context, startupOrder("sub"));
        assertTrue(at.startsWith(". Waiting at: sub/deep ("), at);
    }

    @Test
    public void atMostThreePlacesAreNamed() throws Exception {
        context.getInflightRepository().setInflightBrowseEnabled(true);
        context.getShutdownStrategy().setTimeout(1);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);

        for (int i = 1; i <= 4; i++) {
            template.sendBody("seda:w" + i, "A");
            awaitAt("wait" + i);
        }

        String at = DefaultShutdownStrategy.waitingAt(context, context.getCamelContextExtension().getRouteStartupOrder());
        assertTrue(at.startsWith(". Waiting at: "), at);
        assertTrue(at.endsWith(" and 1 more"), at);
        // three places named, such as w1/wait1 (...), the fourth counted
        assertEquals(3, at.split("/wait", -1).length - 1, at);
    }

    private void awaitAt(String nodeId) {
        // the node id is set once the exchange is at the node, after it was added to the inflight repository
        await().atMost(5, TimeUnit.SECONDS).until(() -> context.getInflightRepository().browse().stream()
                .anyMatch(inflight -> nodeId.equals(inflight.getNodeId())));
    }

    private List<RouteStartupOrder> startupOrder(String routeId) {
        return context.getCamelContextExtension().getRouteStartupOrder().stream()
                .filter(order -> routeId.equals(order.getRoute().getId()))
                .toList();
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

                from("seda:caller").routeId("caller")
                        .to("direct:sub");
                from("direct:sub").routeId("sub")
                        .to("direct:missing").id("deep");

                for (int i = 1; i <= 4; i++) {
                    from("seda:w" + i).routeId("w" + i)
                            .to("direct:missing" + i).id("wait" + i);
                }
            }
        };
    }
}
