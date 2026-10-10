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
package org.apache.camel.component.google.mail.stream;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A message whose exchange is still being routed asynchronously must not be consumed again by the next poll, and a
 * message whose exchange failed, whose get failed, or whose exchange a stopping consumer did not process, is consumed
 * again.
 */
class GoogleMailStreamConsumerInProgressTest extends CamelTestSupport {

    private final FakeGmailClientFactory gmail = new FakeGmailClientFactory();
    private final CountDownLatch inRoute = new CountDownLatch(1);
    private final CountDownLatch twoPolls = new CountDownLatch(2);
    private final CountDownLatch failedPoll = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger attempts = new AtomicInteger();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        gmail.addUnread("m1", "hello");
        CamelContext context = super.createCamelContext();
        context.getComponent("google-mail-stream", GoogleMailStreamComponent.class).setClientFactory(gmail);
        // counts the polls that completed (the exchanges of the poll were handed to the route)
        context.getRegistry().bind("countPolls", new PollingConsumerPollStrategy() {
            @Override
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            @Override
            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                twoPolls.countDown();
            }

            @Override
            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) {
                failedPoll.countDown();
                return false;
            }
        });
        return context;
    }

    @AfterEach
    void releaseRoute() {
        release.countDown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // seda hands the on completions (mark as read) over to these routes
                from("seda:hold")
                        .process(e -> {
                            inRoute.countDown();
                            release.await(20, TimeUnit.SECONDS);
                        })
                        .to("mock:result");

                from("seda:failFirst")
                        .process(e -> {
                            if (attempts.incrementAndGet() == 1) {
                                throw new IllegalStateException("first attempt fails");
                            }
                        })
                        .to("mock:result");
            }
        };
    }

    private void consumeTo(String uri) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("google-mail-stream://index?clientId=id&clientSecret=secret&delay=10&pollStrategy=#countPolls")
                        .to(uri);
            }
        });
    }

    @Test
    void messageInFlightIsNotConsumedAgain() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("hello");

        consumeTo("seda:hold");

        assertTrue(inRoute.await(20, TimeUnit.SECONDS), "the message was not consumed");
        // the second poll has completed while the first exchange is still held in the seda route
        assertTrue(twoPolls.await(20, TimeUnit.SECONDS), "the consumer did not poll again");
        int gets = gmail.gets("m1");
        release.countDown();

        assertEquals(1, gets, "m1 was consumed again by a later poll while its first exchange was still in flight");
        mock.assertIsSatisfied();
        assertTrue(gmail.unexpectedRequests().isEmpty(), () -> "unexpected requests " + gmail.unexpectedRequests());
    }

    @Test
    void failedMessageIsConsumedAgain() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("hello");

        consumeTo("seda:failFirst");

        mock.assertIsSatisfied();
        assertEquals(2, attempts.get());
        assertTrue(gmail.awaitMarkedAsRead(20, TimeUnit.SECONDS), "the message was not marked as read");
        assertFalse(gmail.isUnread("m1"), "the message is unread again");
        assertTrue(gmail.unexpectedRequests().isEmpty(), () -> "unexpected requests " + gmail.unexpectedRequests());
    }

    @Test
    void messageIsConsumedAfterAFailedGet() throws Exception {
        // the get of the first poll fails, which must not leave the message in progress
        gmail.failNextGet("m1");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("hello");

        consumeTo("mock:result");

        mock.assertIsSatisfied();
        assertEquals(0, failedPoll.getCount(), "the get of the first poll did not fail");
        assertTrue(gmail.unexpectedRequests().isEmpty(), () -> "unexpected requests " + gmail.unexpectedRequests());
    }

    @Test
    void notProcessedMessageIsConsumedAgain() throws Exception {
        GoogleMailStreamEndpoint endpoint = context.getEndpoint(
                "google-mail-stream://index?clientId=id&clientSecret=secret", GoogleMailStreamEndpoint.class);
        // never started, so the consumer does not process any exchange of the batch
        GoogleMailStreamConsumer consumer = (GoogleMailStreamConsumer) endpoint.createConsumer(exchange -> {
        });

        consumer.poll();
        consumer.poll();

        assertEquals(2, gmail.gets("m1"), "m1 stayed in progress although its exchange was not processed");
    }
}
