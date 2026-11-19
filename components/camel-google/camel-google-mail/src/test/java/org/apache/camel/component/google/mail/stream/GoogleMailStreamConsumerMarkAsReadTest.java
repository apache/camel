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

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With markAsRead, the consumer marks the mail it consumed as read, whatever the route did with the
 * {@link GoogleMailStreamConstants#MAIL_ID} header.
 */
class GoogleMailStreamConsumerMarkAsReadTest extends CamelTestSupport {

    private static final String URI = "google-mail-stream://index?clientId=id&clientSecret=secret&delay=10";

    private final FakeGmailClientFactory gmail = new FakeGmailClientFactory();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getComponent("google-mail-stream", GoogleMailStreamComponent.class).setClientFactory(gmail);
        return context;
    }

    @Test
    void marksTheConsumedMailAsReadWhenTheRouteRemovedTheHeaders() throws Exception {
        gmail.addUnread("m1", "hello");
        gmail.addUnread("m2", "world");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("hello", "world");
        // consumed once: marked as read, so the later polls must not route it again
        mock.setAssertPeriod(500);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(URI).removeHeaders("*").to("mock:result");
            }
        });

        mock.assertIsSatisfied();
        await().atMost(10, TimeUnit.SECONDS).until(() -> !gmail.isUnread("m1") && !gmail.isUnread("m2"));
        // marked as read, so not listed again by the next polls
        assertEquals(1, gmail.gets("m1"));
        assertEquals(1, gmail.gets("m2"));
        assertEquals(List.of(), gmail.unexpectedRequests());
    }

    @Test
    void marksTheConsumedMailAsReadWhenTheRouteChangedTheHeader() throws Exception {
        gmail.addUnread("m1", "hello");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("hello");
        // consumed once: marked as read, so the later polls must not route it again
        mock.setAssertPeriod(500);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(URI)
                        .setHeader(GoogleMailStreamConstants.MAIL_ID, constant("another-mail"))
                        .to("mock:result");
            }
        });

        mock.assertIsSatisfied();
        await().atMost(10, TimeUnit.SECONDS).until(() -> !gmail.isUnread("m1"));
        // marked as read, so not listed again by the next polls
        assertEquals(1, gmail.gets("m1"));
    }
}
