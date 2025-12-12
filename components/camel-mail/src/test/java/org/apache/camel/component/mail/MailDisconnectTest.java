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
package org.apache.camel.component.mail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit test for batch consumer.
 */
public class MailDisconnectTest extends CamelTestSupport {
    private static final MailboxUser jones = Mailbox.getOrCreateUser("jones", "secret");

    private int expectedCount = 5;
    private CountDownLatch latch = new CountDownLatch(expectedCount);

    void waitForConnections() {
        Awaitility.await().until(() -> context.getRoute("mail-disconnect").getUptimeMillis() > 1000);
    }

    @BeforeEach
    void setup() {
        Mailbox.clearAll();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(expectedCount);

        // send 5 mails with some delay so we do multiple polls with disconnect between
        template.sendBodyAndHeader(jones.uriPrefix(Protocol.smtp), "A Bla bla", "Subject", "Hello A");
        template.sendBodyAndHeader(jones.uriPrefix(Protocol.smtp), "B Bla bla", "Subject", "Hello B");
        template.sendBodyAndHeader(jones.uriPrefix(Protocol.smtp), "C Bla bla", "Subject", "Hello C");
        template.sendBodyAndHeader(jones.uriPrefix(Protocol.smtp), "D Bla bla", "Subject", "Hello D");
        template.sendBodyAndHeader(jones.uriPrefix(Protocol.smtp), "E Bla bla", "Subject", "Hello E");

        waitForConnections();
    }

    @Test
    public void testDisconnect() throws Exception {
        if (!latch.await(2500, TimeUnit.MILLISECONDS)) {
            Assertions.fail("Not all messages were received as expected: " + (expectedCount - latch.getCount())
                            + " message(s) missing");
        }

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(jones.uriPrefix(Protocol.imap) + "&disconnect=true&initialDelay=100&delay=500")
                        .routeId("mail-disconnect")
                        .process(e -> latch.countDown())
                        .to("mock:result");
            }
        };
    }
}
