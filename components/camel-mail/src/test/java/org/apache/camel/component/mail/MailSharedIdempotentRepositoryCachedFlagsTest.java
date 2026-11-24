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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.support.KeyValueIdempotentRepository;
import org.apache.camel.support.UnitOfWorkHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two consumers share an idempotent repository. Consumer B routed the mail once and its exchange failed (the key is
 * removed, the mail stays unseen), so B's mail object has loaded the flags of the mail (not SEEN). In B's next poll,
 * after B searched the unseen mails, consumer A consumes the mail: it marks it SEEN and removes its key. B then adds
 * the key, which succeeds. B must ask the server whether the mail is SEEN: with closeFolder=false B's folder stays open
 * and the search returns the mail object of the first poll, whose flags are cached. A runs inside B's mailUidGenerator,
 * between B's search and B's add, so the test needs no threads.
 */
class MailSharedIdempotentRepositoryCachedFlagsTest extends CamelTestSupport {
    private static final MailboxUser kim = Mailbox.getOrCreateUser("MailSharedIdempotentRepositoryCachedFlagsTest-kim",
            "secret");

    @BindToRegistry("repo")
    private final KeyValueIdempotentRepository repo = new KeyValueIdempotentRepository();

    private final Queue<Exchange> queuedA = new ConcurrentLinkedQueue<>();
    private final Queue<Exchange> queuedB = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean consumeByA = new AtomicBoolean();
    private final AtomicReference<MailConsumer> consumerA = new AtomicReference<>();

    @BindToRegistry("gateB")
    private final MailUidGenerator gateB = (endpoint, message) -> {
        String key = new DefaultMailUidGenerator().generateUuid(endpoint, message);
        if (consumeByA.compareAndSet(true, false)) {
            try {
                assertEquals(1, consumerA.get().poll());
                complete(queuedA.poll());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            assertEquals(0, kim.getInbox().getNewMessageCount(), "A marked the mail SEEN");
            assertFalse(repo.contains(key), "A removed the key of the mail");
        }
        return key;
    };

    @Override
    public void setupResources() throws Exception {
        Mailbox.clearAll(kim);
        JavaMailSender sender = new DefaultJavaMailSender();
        Store store = sender.getSession().getStore("imap");
        store.connect("localhost", Mailbox.getPort(Protocol.imap), kim.getLogin(), kim.getPassword());
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
    void folderKeptOpen() throws Exception {
        mailConsumedByAnotherConsumerIsNotRoutedAgain("&closeFolder=false");
    }

    @Test
    void folderClosedAfterEachPoll() throws Exception {
        mailConsumedByAnotherConsumerIsNotRoutedAgain("");
    }

    private void mailConsumedByAnotherConsumerIsNotRoutedAgain(String options) throws Exception {
        consumerA.set(createConsumer("&idempotentRepository=#repo", queuedA));
        MailConsumer consumerB = createConsumer("&idempotentRepository=#repo&mailUidGenerator=#gateB" + options, queuedB);
        try {
            // B routes the mail, and the exchange fails: the key is removed, the mail stays unseen
            assertEquals(1, consumerB.poll());
            Exchange first = queuedB.poll();
            assertNotNull(first);
            first.setException(new IllegalStateException("Forced"));
            complete(first);
            assertEquals(1, kim.getInbox().getNewMessageCount());

            // B polls again; A consumes the mail between B's search and B's add
            consumeByA.set(true);
            assertEquals(0, consumerB.poll(), "B does not route the mail that A consumed");
            assertFalse(consumeByA.get(), "A consumed the mail during B's poll");
            assertTrue(queuedB.isEmpty());
        } finally {
            consumerB.stop();
            consumerA.get().stop();
        }
    }

    /**
     * A consumer whose processor only queues the exchange, as the polling consumer of pollEnrich and ConsumerTemplate
     * does: the exchange completes later, on the test thread.
     */
    private MailConsumer createConsumer(String options, Queue<Exchange> queue) throws Exception {
        MailEndpoint endpoint = context.getEndpoint(kim.uriPrefix(Protocol.imap) + options, MailEndpoint.class);
        JavaMailSender sender = endpoint.getConfiguration().createJavaMailSender(context);
        endpoint.getConfiguration().configureJavaMailSender(context, sender);
        MailConsumer consumer = new MailConsumer(endpoint, queue::add, sender);
        consumer.setStartScheduler(false);
        consumer.start();
        return consumer;
    }

    private static void complete(Exchange exchange) {
        UnitOfWorkHelper.doneSynchronizations(exchange, exchange.getExchangeExtension().handoverCompletions());
    }
}
