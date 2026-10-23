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
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A mail read with mapMailMessage=false is a read-only message of the store, which an smtp producer can still send.
 */
public class MailForwardRawMessageTest extends CamelTestSupport {

    private static final MailboxUser source = Mailbox.getOrCreateUser("MailForwardRawMessageTest-source", "secret");
    private static final MailboxUser target = Mailbox.getOrCreateUser("MailForwardRawMessageTest-target", "secret");

    @Override
    protected void setupResources() throws Exception {
        Mailbox.clearAll(source, target);

        JavaMailSender sender = new DefaultJavaMailSender();
        Store store = sender.getSession().getStore("imap");
        store.connect("localhost", Mailbox.getPort(Protocol.imap), source.getLogin(), source.getPassword());
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_WRITE);
        MimeMessage msg = new MimeMessage(sender.getSession());
        msg.setFrom(new InternetAddress("someone@localhost"));
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress(target.getEmail()));
        msg.setSubject("Forward me");
        msg.setText("Hello from the store");
        folder.appendMessages(new Message[] { msg });
        folder.close(true);
        store.close();
    }

    @Override
    protected void cleanupResources() throws Exception {
        Mailbox.clearAll(source, target);
    }

    @Test
    public void testForwardStoreMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:sent");
        mock.expectedMessageCount(1);

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertNull(exchange.getException());
        Mailbox inbox = target.getInbox();
        assertEquals(1, inbox.getMessageCount());
        assertEquals("Forward me", inbox.get(0).getSubject());
        assertEquals("Hello from the store", inbox.get(0).getContent().toString().trim());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(source.uriPrefix(Protocol.pop3) + "&initialDelay=100&delay=100&delete=true&mapMailMessage=false")
                        .to(target.uriPrefix(Protocol.smtp))
                        .to("mock:sent");
            }
        };
    }
}
