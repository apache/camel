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
package org.apache.camel.component.nats;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.nats.NatsConsumerPullConsumerDeletedTest.FakeServer;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the JetStream pull consumer does when the server no longer has its consumer, besides creating an ephemeral
 * consumer again (see {@link NatsConsumerPullConsumerDeletedTest}): a durable consumer is not created again, a failed
 * subscribe is tried again, and a start time is not sent together with the start sequence.
 */
class NatsConsumerPullConsumerGoneTest extends CamelTestSupport {

    private final FakeServer server = new FakeServer();
    private final List<Throwable> handled = new CopyOnWriteArrayList<>();

    @Override
    protected RouteBuilder createRouteBuilder() {
        context.getRegistry().bind("conn", server.connection());
        context.getRegistry().bind("eh", new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                handled.add(exception);
            }

            @Override
            public void handleException(String message, Throwable exception) {
                handled.add(exception);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                handled.add(exception);
            }
        });
        return new RouteBuilder() {
            @Override
            public void configure() {
                // each test adds its route
            }
        };
    }

    private void addRoute(String options) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("nats:orders?connection=#conn&jetstreamEnabled=true&jetstreamName=ORDERS&pullFetchTimeout=100"
                     + "&exceptionHandler=#eh" + options)
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        });
    }

    @Test
    void durableConsumerIsNotCreatedAgain() throws Exception {
        List<String> warnings = new CopyOnWriteArrayList<>();
        AbstractAppender appender = new AbstractAppender("CaptureWarn", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel() == Level.WARN
                        && event.getMessage().getFormattedMessage().contains("durable JetStream consumer")) {
                    warnings.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        Logger logger = (Logger) LogManager.getLogger(NatsConsumer.class);
        logger.addAppender(appender);
        try {
            addRoute("&durableName=orders");
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedBodiesReceived("order-1");
            server.publish("order-1");
            mock.assertIsSatisfied();

            // an administrator deleted the durable: a waiting pull gets 409 Consumer Deleted, then the fetches get
            // nothing and the consumer check (every 5 seconds) finds it gone
            server.deleteConsumers(true);
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, warnings.size()));
            await().during(6, TimeUnit.SECONDS).atMost(8, TimeUnit.SECONDS).untilAsserted(() -> {
                assertEquals(1, server.consumersCreated(), "the durable is not created again");
                assertEquals(1, warnings.size(), "the WARN is logged once");
            });

            // the durable is created again with the same name: the same subscription fetches again
            mock.reset();
            mock.expectedBodiesReceived("order-2");
            server.restoreConsumers();
            server.publish("order-2");
            mock.setResultWaitTime(20_000);
            mock.assertIsSatisfied();
            assertEquals(1, server.consumersCreated());
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    @Test
    void failedSubscribeIsTriedAgainAtTheNextCheck() throws Exception {
        addRoute("");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("order-1");
        server.publish("order-1");
        mock.assertIsSatisfied();

        // the stream itself is gone for a while (for example a memory stream after a server restart): jnats reports
        // that subscribe failure as an IllegalStateException
        server.failNextSubscribe(new IllegalStateException("[SUB-90007] No matching streams for subject."));
        server.deleteConsumers(false);

        mock.reset();
        mock.expectedBodiesReceived("order-2");
        server.publish("order-2");
        mock.setResultWaitTime(30_000);
        mock.assertIsSatisfied();
        assertEquals(2, server.consumersCreated(), "created again at the check after the failed one");
        assertTrue(handled.stream().anyMatch(IllegalStateException.class::isInstance),
                "the failed subscribe goes to the exception handler");
    }

    @Test
    void startTimeIsNotSentWithTheStartSequence() throws Exception {
        context.getRegistry().bind("cc", ConsumerConfiguration.builder()
                .deliverPolicy(DeliverPolicy.ByStartTime)
                .startTime(ZonedDateTime.now().minusHours(1))
                .build());
        addRoute("&consumerConfiguration=#cc");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("order-1");
        server.publish("order-1");
        mock.assertIsSatisfied();

        server.deleteConsumers(true);

        mock.reset();
        mock.expectedBodiesReceived("order-2");
        server.publish("order-2");
        mock.setResultWaitTime(20_000);
        mock.assertIsSatisfied();
        ConsumerConfiguration again = server.subscribed().get(1);
        assertEquals(DeliverPolicy.ByStartSequence, again.getDeliverPolicy());
        assertEquals(2, again.getStartSequence());
        assertNull(again.getStartTime());
    }
}
