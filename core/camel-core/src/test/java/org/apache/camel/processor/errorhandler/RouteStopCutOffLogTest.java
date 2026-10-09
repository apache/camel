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
package org.apache.camel.processor.errorhandler;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.log.ConsumingAppender;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25484: an exchange that a route stop or a dev mode reload cuts off is logged as one WARN line, not as an
 * exhausted failure with message history and stack trace.
 */
public class RouteStopCutOffLogTest extends ContextTestSupport {

    private static final String LOGGER = DefaultErrorHandler.class.getName();

    private final Queue<String> messages = new ConcurrentLinkedQueue<>();

    @AfterEach
    public void removeAppender() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().removeLogger(LOGGER);
        ctx.updateLoggers();
    }

    @Test
    public void theCutOffIsOneWarnLine() throws Exception {
        ConsumingAppender.newAppender(LOGGER, "RouteStopCutOffLogTest", Level.DEBUG,
                event -> messages.add(event.getLevel() + " " + event.getMessage().getFormattedMessage()
                                      + (event.getThrown() != null ? " THROWN" : "")));
        context.getShutdownStrategy().setTimeout(1);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);

        context.getInflightRepository().setInflightBrowseEnabled(true);

        template.sendBody("seda:start", List.of("A", "B"));
        // the exchange waits for a consumer on direct:shipment, as after a save that sends to a route not written yet
        await().atMost(5, TimeUnit.SECONDS).until(() -> context.getInflightRepository().browse().stream()
                .anyMatch(e -> "shipment".equals(e.getNodeId())));
        context.getRouteController().stopRoute("slow");

        await().atMost(5, TimeUnit.SECONDS).until(() -> !messages.isEmpty());
        assertEquals(1, messages.size(), messages.toString());
        String message = messages.peek();
        assertTrue(message.startsWith("WARN Exchange cut off (MessageId: "), message);
        assertTrue(message.contains(": its route is being stopped or reloaded, so it is not continued."), message);
        assertFalse(message.contains("Exhausted"), message);
        assertFalse(message.contains("Message History"), message);
        assertFalse(message.endsWith("THROWN"), message);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:start").routeId("slow")
                        .split(body())
                            .log("Picked ${body}")
                            .to("direct:shipment").id("shipment")
                        .end()
                        .to("mock:result");
            }
        };
    }
}
