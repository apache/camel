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

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for idempotent repository.
 */
public class MailIdempotentRepositoryDuplicateTest extends CamelTestSupport {
    protected static final MailboxUser jones = Mailbox.getOrCreateUser("jones", "secret");

    @BindToRegistry("myRepo")
    MemoryIdempotentRepository myRepo = new MemoryIdempotentRepository();

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        prepareMailbox();
        super.setUp();
    }

    @Test
    public void testIdempotent() throws Exception {
        assertEquals(1, myRepo.getCacheSize());

        MockEndpoint mock = getMockEndpoint("mock:result");
        // no 3 is already in the idempotent repo
        mock.expectedBodiesReceived("Message 0\r\n", "Message 1\r\n", "Message 2\r\n", "Message 4\r\n");

        context.getRouteController().startRoute("foo");

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);

        // windows need a little slack
        Awaitility.await().atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertEquals(0, jones.getInbox().getNewMessageCount()));

        // they are removed on confirm
        assertEquals(1, myRepo.getCacheSize());
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
            messages[i].setText("Message " + i);
            messages[i].setHeader("Message-ID", "myuid-" + i);
        }
        folder.appendMessages(messages);

        // mark the message #3 as known
        myRepo.add(folder.getMessages()[3].getHeader("Message-ID")[0]);
        folder.close(true);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(jones.uriPrefix(Protocol.pop3) + "&idempotentRepository=#myRepo&initialDelay=100&delay=100")
                        .routeId("foo").noAutoStartup()
                        .to("mock:result");
            }
        };
    }
}
