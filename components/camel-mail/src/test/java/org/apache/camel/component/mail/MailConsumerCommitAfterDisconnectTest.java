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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.KeyValueIdempotentRepository;
import org.apache.camel.support.UnitOfWorkHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With pollEnrich or a ConsumerTemplate, the exchange of a mail completes after the poll that retrieved it. With
 * disconnect=true that poll has disconnected (the consumer has no folder any more) when the mail is marked: the mail
 * must still be marked (the consumer connects again), and its idempotent key settled.
 */
class MailConsumerCommitAfterDisconnectTest extends CamelTestSupport {
    private static final MailboxUser ann = Mailbox.getOrCreateUser("MailConsumerCommitAfterDisconnectTest-ann", "secret");

    @BindToRegistry("repo")
    private final KeyValueIdempotentRepository repo = new KeyValueIdempotentRepository();

    private final Queue<Exchange> queued = new ConcurrentLinkedQueue<>();

    @Override
    public void setupResources() throws Exception {
        Mailbox.clearAll(ann);
        JavaMailSender sender = new DefaultJavaMailSender();
        Store store = sender.getSession().getStore("imap");
        store.connect("localhost", Mailbox.getPort(Protocol.imap), ann.getLogin(), ann.getPassword());
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_WRITE);
        Message message = new MimeMessage(sender.getSession());
        message.setSubject("Mail 0");
        message.setText("Message 0");
        folder.appendMessages(new Message[] { message });
        folder.close(true);
        store.close();
    }

    @Test
    void mailIsMarkedSeenWhenItsExchangeCompletesAfterTheConsumerDisconnected() throws Exception {
        MailConsumer consumer = createConsumer(ann.uriPrefix(Protocol.imap) + "&disconnect=true&idempotentRepository=#repo");
        try {
            assertEquals(1, consumer.poll());
            Exchange exchange = queued.poll();
            assertNotNull(exchange);
            String key = exchange.getProperty(MailConsumer.MAIL_MESSAGE_UID, String.class);
            assertTrue(repo.contains(key));

            // the poll disconnected; the exchange completes now (ConsumerTemplate.doneUoW, or the end of pollEnrich)
            complete(exchange);

            assertEquals(0, ann.getInbox().getNewMessageCount(), "the mail of the completed exchange is marked SEEN");
            assertFalse(repo.contains(key), "the key of the mail is removed on commit");
            // the next poll does not route the mail again
            assertEquals(0, consumer.poll());
        } finally {
            consumer.stop();
        }
    }

    @Test
    void mailIsDeletedWhenItsExchangeCompletesAfterTheConsumerDisconnected() throws Exception {
        MailConsumer consumer = createConsumer(ann.uriPrefix(Protocol.imap) + "&disconnect=true&delete=true");
        try {
            assertEquals(1, consumer.poll());
            complete(queued.poll());

            // flagged DELETED, and expunged when the consumer disconnected again
            assertEquals(0, ann.getInbox().getMessageCount(), "the mail of the completed exchange is deleted");
        } finally {
            consumer.stop();
        }
    }

    @Test
    void pop3MailIsDeletedWhenItsExchangeCompletesAfterTheConsumerDisconnected() throws Exception {
        MailConsumer consumer = createConsumer(ann.uriPrefix(Protocol.pop3) + "&disconnect=true&delete=true");
        try {
            assertEquals(1, consumer.poll());
            complete(queued.poll());

            assertEquals(0, ann.getInbox().getMessageCount(), "the mail of the completed exchange is deleted");
        } finally {
            consumer.stop();
        }
    }

    @Test
    void pollEnrichWithDisconnectDeletesTheMail() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Message 0");

        template.sendBody("direct:start", "");

        MockEndpoint.assertIsSatisfied(context);
        // the exchange completed after the poll of the pollEnrich consumer disconnected
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, ann.getInbox().getMessageCount(), "the enriched mail is deleted"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .pollEnrich(ann.uriPrefix(Protocol.imap) + "&disconnect=true&delete=true&initialDelay=10&delay=100",
                                5000)
                        .to("mock:result");
            }
        };
    }

    /**
     * A consumer whose processor only queues the exchange, as the polling consumer of pollEnrich and ConsumerTemplate
     * does: the exchange completes later, on the test thread.
     */
    private MailConsumer createConsumer(String uri) throws Exception {
        MailEndpoint endpoint = context.getEndpoint(uri, MailEndpoint.class);
        JavaMailSender sender = endpoint.getConfiguration().createJavaMailSender(context);
        endpoint.getConfiguration().configureJavaMailSender(context, sender);
        MailConsumer consumer = new MailConsumer(endpoint, queued::add, sender);
        consumer.setStartScheduler(false);
        consumer.start();
        return consumer;
    }

    private static void complete(Exchange exchange) {
        UnitOfWorkHelper.doneSynchronizations(exchange, exchange.getExchangeExtension().handoverCompletions());
    }
}
