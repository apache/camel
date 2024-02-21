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

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for idempotent repository.
 */
public class MailIdempotentRepositoryDuplicateNotRemoveTest extends MailIdempotentRepositoryDuplicateTest {

    @Override
    @Test
    public void testIdempotent() throws Exception {
        assertEquals(1, myRepo.getCacheSize());

        MockEndpoint mock = getMockEndpoint("mock:result");
        // no 3 is already in the idempotent repo
        mock.expectedBodiesReceived("Message 0\r\n", "Message 1\r\n", "Message 2\r\n", "Message 4\r\n");

        context.getRouteController().startRoute("foo");

        MockEndpoint.assertIsSatisfied(context);

        // windows need a little slack
        Awaitility.await().atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertEquals(0, jones.getInbox().getNewMessageCount()));

        // they are not removed so we should have all 5 in the repo now
        assertEquals(5, myRepo.getCacheSize());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(jones.uriPrefix(Protocol.pop3)
                     + "&idempotentRepository=#myRepo&idempotentRepositoryRemoveOnCommit=false&initialDelay=100&delay=100")
                        .routeId("foo").noAutoStartup()
                        .to("mock:result");
            }
        };
    }
}
