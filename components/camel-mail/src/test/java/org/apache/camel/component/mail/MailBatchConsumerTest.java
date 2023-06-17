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

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.body;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for batch consumer.
 */
public class MailBatchConsumerTest extends CamelTestSupport {
    private static final MailboxUser jones = Mailbox.getOrCreateUser("jones", "secret");

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        prepareMailbox();
        super.setUp();
    }

    @Test
    public void testBatchConsumer() throws Exception {
        Mailbox mailbox = jones.getInbox();
        assertEquals(5, mailbox.getMessageCount());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(5);
        mock.expectsAscending(body());
        mock.message(0).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(0);
        mock.message(1).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(1);
        mock.message(2).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(2);
        mock.message(3).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(3);
        mock.message(4).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(4);
        mock.message(0).exchangeProperty(Exchange.BATCH_COMPLETE).isEqualTo(false);
        mock.message(1).exchangeProperty(Exchange.BATCH_COMPLETE).isEqualTo(false);
        mock.message(2).exchangeProperty(Exchange.BATCH_COMPLETE).isEqualTo(false);
        mock.message(3).exchangeProperty(Exchange.BATCH_COMPLETE).isEqualTo(false);
        mock.message(3).exchangeProperty(Exchange.BATCH_COMPLETE).isEqualTo(false);
        mock.message(4).exchangeProperty(Exchange.BATCH_COMPLETE).isEqualTo(true);
        mock.expectedPropertyReceived(Exchange.BATCH_SIZE, 5);

        MockEndpoint.assertIsSatisfied(context);
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
                from(jones.uriPrefix(Protocol.pop3) + "&initialDelay=100&delay=100"
                     + "&delete=true").to("mock:result");
            }
        };
    }
}
