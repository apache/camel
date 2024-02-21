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

import jakarta.mail.internet.InternetAddress;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for Mail replyTo support.
 */
public class MailReplyToTest extends CamelTestSupport {
    private static final MailboxUser christian = Mailbox.getOrCreateUser("christian", "secret");

    @Test
    public void testMailReplyTo() throws Exception {
        Mailbox.clearAll();

        String body = "The Camel riders";

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MailConstants.MAIL_REPLY_TO, "noReply1@localhost,noReply2@localhost");
        mock.expectedBodiesReceived(body);

        template.sendBodyAndHeader("direct:a", body, MailConstants.MAIL_REPLY_TO, "noReply1@localhost,noReply2@localhost");

        mock.assertIsSatisfied();

        Mailbox mailbox = christian.getInbox();
        assertEquals(1, mailbox.getMessageCount());
        assertEquals("noReply1@localhost", ((InternetAddress) mailbox.get(0).getReplyTo()[0]).getAddress());
        assertEquals("noReply2@localhost", ((InternetAddress) mailbox.get(0).getReplyTo()[1]).getAddress());
        assertEquals(body, mailbox.get(0).getContent());
    }

    @Test
    public void testMailReplyTo2() throws Exception {
        Mailbox.clearAll();

        String body = "The Camel riders";

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MailConstants.MAIL_REPLY_TO, "noReply1@localhost, noReply2@localhost");
        mock.expectedBodiesReceived(body);

        template.sendBody("direct:b", body);

        mock.assertIsSatisfied();

        Mailbox mailbox = christian.getInbox();
        assertEquals(1, mailbox.getMessageCount());
        assertEquals("noReply1@localhost", ((InternetAddress) mailbox.get(0).getReplyTo()[0]).getAddress());
        assertEquals("noReply2@localhost", ((InternetAddress) mailbox.get(0).getReplyTo()[1]).getAddress());
        assertEquals(body, mailbox.get(0).getContent());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a")
                        .to(christian.uriPrefix(Protocol.smtp) + "&subject=Camel");

                from("direct:b")
                        .to(christian.uriPrefix(Protocol.smtp)
                            + "&subject=Camel&replyTo=noReply1@localhost,noReply2@localhost");

                from(christian.uriPrefix(Protocol.imap) + "&initialDelay=100&delay=100")
                        .to("mock:result");
            }
        };
    }
}
