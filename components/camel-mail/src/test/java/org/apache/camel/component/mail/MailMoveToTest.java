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

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for moveTo.
 */
public class MailMoveToTest extends CamelTestSupport {
    private static final MailboxUser jones = Mailbox.getOrCreateUser("jones", "secret");
    private static final MailboxUser jones2 = Mailbox.getOrCreateUser("jones2", "secret");

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        prepareMailbox();
        super.setUp();
    }

    @Test
    public void testMoveToWithMarkAsSeen() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(5);
        MockEndpoint.assertIsSatisfied(context);

        Awaitility.await().atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertEquals(0, jones.getInbox().getMessageCount()));
        assertEquals(0, jones.getInbox().getNewMessageCount());

        final Mailbox moveToFolder = jones.getFolder("moveToFolder");
        assertEquals(5, moveToFolder.getMessageCount());
        assertEquals(0, moveToFolder.getNewMessageCount());

        assertTrue(moveToFolder.get(0).getFlags().contains(Flags.Flag.SEEN));
        assertTrue(moveToFolder.get(1).getFlags().contains(Flags.Flag.SEEN));
        assertTrue(moveToFolder.get(2).getFlags().contains(Flags.Flag.SEEN));
        assertTrue(moveToFolder.get(3).getFlags().contains(Flags.Flag.SEEN));
        assertTrue(moveToFolder.get(4).getFlags().contains(Flags.Flag.SEEN));
    }

    @Test
    public void testMoveToWithDelete() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result2");
        mock.expectedMessageCount(5);
        MockEndpoint.assertIsSatisfied(context);

        Awaitility.await().atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertEquals(0, jones2.getInbox().getMessageCount()));
        assertEquals(0, jones2.getInbox().getNewMessageCount());
        assertEquals(5, jones2.getFolder("moveToFolder").getNewMessageCount());

        assertFalse(jones2.getFolder("moveToFolder").get(0).getFlags().contains(Flags.Flag.SEEN));
        assertFalse(jones2.getFolder("moveToFolder").get(1).getFlags().contains(Flags.Flag.SEEN));
        assertFalse(jones2.getFolder("moveToFolder").get(2).getFlags().contains(Flags.Flag.SEEN));
        assertFalse(jones2.getFolder("moveToFolder").get(3).getFlags().contains(Flags.Flag.SEEN));
        assertFalse(jones2.getFolder("moveToFolder").get(4).getFlags().contains(Flags.Flag.SEEN));
    }

    private void prepareMailbox() throws Exception {
        Mailbox.clearAll();
        MailboxUser[] mailUser = new MailboxUser[] { jones, jones2 };
        for (MailboxUser user : mailUser) {
            JavaMailSender sender = new DefaultJavaMailSender();
            Store store = sender.getSession().getStore("imap");
            store.connect("localhost", Mailbox.getPort(Protocol.imap), user.getLogin(), user.getPassword());
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
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() {
        return new RoutesBuilder[] { new RouteBuilder() {
            public void configure() {
                from(jones.uriPrefix(Protocol.imap) + "&delete=false&moveTo=moveToFolder&initialDelay=100&delay=100")
                        .to("mock:result");
            }
        }, new RouteBuilder() {
            public void configure() {
                from(jones2.uriPrefix(Protocol.imap) + "&delete=true&moveTo=moveToFolder&initialDelay=100&delay=100")
                        .to("mock:result2");
            }
        } };
    }
}
