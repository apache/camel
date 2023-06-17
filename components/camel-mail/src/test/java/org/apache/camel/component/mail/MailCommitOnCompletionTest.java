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

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for delete mail runs as an onCompletion.
 */
public class MailCommitOnCompletionTest extends CamelTestSupport {
    private static final MailboxUser jones = Mailbox.getOrCreateUser("jonesCommitOnCompletion", "secret");

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        prepareMailbox();
        super.setUp();
    }

    @Test
    public void testCommitOnCompletion() throws Exception {
        Mailbox mailbox = jones.getInbox();
        assertEquals(5, mailbox.getMessageCount());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hi Message 0\r\n", "Hi Message 1\r\n", "Hi Message 2\r\n", "Hi Message 3\r\n",
                "Hi Message 4\r\n");

        mock.assertIsSatisfied();

        // wait a bit because delete is on completion
        await().atMost(5000, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertEquals(0, jones.getInbox().getMessageCount()));
    }

    private void prepareMailbox() throws Exception {
        // connect to mailbox
        Mailbox.clearAll();
        JavaMailSender sender = new DefaultJavaMailSender();
        Store store = sender.getSession().getStore("imap");
        store.connect("localhost", Mailbox.getPort(Protocol.imap), jones.getLogin(), jones.getPassword());
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_WRITE);
        folder.expunge();

        // inserts 5 new messages
        Message[] messages = new Message[5];
        for (int i = 0; i < 5; i++) {
            messages[i] = new MimeMessage(sender.getSession());
            messages[i].setHeader("Message-ID", "" + i);
            messages[i].setText("Message " + i);
        }
        folder.appendMessages(messages);
        folder.close(true);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(jones.uriPrefix(Protocol.pop3) + "&delete=true&initialDelay=100&delay=100")
                        .process(exchange -> {
                            String msg = exchange.getIn().getBody(String.class);
                            exchange.getMessage().setBody("Hi " + msg);
                        })
                        .to("mock:result");
            }
        };
    }
}
