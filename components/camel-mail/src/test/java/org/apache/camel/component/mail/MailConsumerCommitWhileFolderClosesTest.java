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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.support.KeyValueIdempotentRepository;
import org.apache.camel.support.UnitOfWorkHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mail of an exchange is checked against the open of the folder in which it was retrieved, then marked. The folder
 * must not be closed or opened again between the two: after an expunge, the message number of the mail object
 * designates another mail in a new open of the folder. The poll closes the folder (closeFolder=true) or disconnects
 * (disconnect=true) at its end, while an exchange that is completed on another thread (pollEnrich, ConsumerTemplate)
 * marks its mail; the connection of the folder can also drop. The mail server is the in-JVM GreenMail IMAP server.
 */
class MailConsumerCommitWhileFolderClosesTest extends CamelTestSupport {
    private static final MailboxUser kai = Mailbox.getOrCreateUser("MailConsumerCommitWhileFolderClosesTest-kai", "secret");

    @BindToRegistry("repo")
    private final KeyValueIdempotentRepository repo = new KeyValueIdempotentRepository();

    private final Map<String, Exchange> queued = new ConcurrentHashMap<>();
    private final ExecutorService threads = Executors.newFixedThreadPool(2);
    private final AtomicReference<Future<?>> completion = new AtomicReference<>();
    private final AtomicReference<String> keyOfMail1 = new AtomicReference<>();

    @Override
    public void setupResources() throws Exception {
        Mailbox.clearAll(kai);
        JavaMailSender sender = new DefaultJavaMailSender();
        Message[] messages = new Message[3];
        for (int i = 0; i < 3; i++) {
            messages[i] = new MimeMessage(sender.getSession());
            messages[i].setSubject("Mail " + i);
            messages[i].setText("Message " + i);
        }
        Store store = openStore();
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_WRITE);
        folder.appendMessages(messages);
        folder.close(true);
        store.close();
    }

    @AfterEach
    void stopThreads() {
        threads.shutdownNow();
    }

    /**
     * The connection of the folder drops after the mail of "Mail 1" was checked, and another client expunges "Mail 0":
     * the mail object of "Mail 1" now has the message number of "Mail 2" in a new open. No mail is marked, the key of
     * "Mail 1" is removed, and the next poll routes "Mail 1" again.
     */
    @Test
    void mailIsNotMarkedWhenTheConnectionDropsAfterItWasChecked() throws Exception {
        MailConsumer consumer = new MailConsumer(endpoint("&idempotentRepository=#repo"), handOff(), sender()) {
            @Override
            protected void processCommit(Message mail, Exchange exchange) {
                if (exchange == queued.get("Mail 1")) {
                    try {
                        // the connection of the folder drops (the consumer's folder is closed)
                        mail.getFolder().close(false);
                        removeByAnotherClient("Mail 0");
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
                super.processCommit(mail, exchange);
            }
        };
        start(consumer);
        try {
            assertEquals(2, consumer.poll());
            Exchange exchange = queued.get("Mail 1");
            String key = exchange.getProperty(MailConsumer.MAIL_MESSAGE_UID, String.class);
            complete(exchange);

            assertFalse(isSeen("Mail 2"), "a mail that was never retrieved is not marked");
            assertFalse(isSeen("Mail 1"), "the mail is not marked by its stale message number");
            assertFalse(repo.contains(key), "the key of the mail that is not marked is removed");
            // the next poll consumes "Mail 1" again (and "Mail 2")
            queued.clear();
            assertEquals(2, consumer.poll());
            assertTrue(queued.containsKey("Mail 1"), "the mail is routed again");
        } finally {
            consumer.stop();
        }
    }

    /**
     * closeFolder=true: the exchange of "Mail 1" completes on another thread while the poll ends. After its mail was
     * checked against the open folder, the poll would close the folder and another client expunges "Mail 0": marking
     * the mail object of "Mail 1" in a new open marks "Mail 2". The poll must wait to close the folder until the mail
     * is marked.
     */
    @Test
    void pollClosesTheFolderAfterACompletingMailIsMarked() throws Exception {
        CountDownLatch expunged = new CountDownLatch(1);
        Future<Integer> poll = runPollWhileMail1Completes("&closeFolder=true", expunged);
        assertEquals(2, poll.get(20, TimeUnit.SECONDS));
        // another client expunges "Mail 0" once the poll closed the folder
        removeByAnotherClient("Mail 0");
        expunged.countDown();
        completion.get().get(20, TimeUnit.SECONDS);

        assertTrue(isSeen("Mail 1"), "the mail of the completed exchange is marked SEEN");
        assertFalse(isSeen("Mail 2"), "a mail that was never retrieved is not marked");
    }

    /**
     * disconnect=true: the poll would disconnect (the consumer has no folder any more) after the mail of "Mail 1" was
     * checked. The poll must wait to disconnect until the mail is marked.
     */
    @Test
    void pollDisconnectsAfterACompletingMailIsMarked() throws Exception {
        Future<Integer> poll = runPollWhileMail1Completes("&disconnect=true&idempotentRepository=#repo", null);
        assertEquals(2, poll.get(20, TimeUnit.SECONDS));
        completion.get().get(20, TimeUnit.SECONDS);
        String key = keyOfMail1.get();

        assertTrue(isSeen("Mail 1"), "the mail of the completed exchange is marked SEEN");
        assertFalse(repo.contains(key), "the key of the mail is removed on commit");
    }

    /**
     * Polls "Mail 0" and "Mail 1" on one thread; the exchange of "Mail 1" completes on another thread. Its mail is
     * checked, then the commit waits until the poll ended or waits to end (on the poll thread, once "Mail 1" was handed
     * off, nothing else parks). When the poll ended, the commit also waits for the given latch.
     */
    private Future<Integer> runPollWhileMail1Completes(String options, CountDownLatch beforeMark) throws Exception {
        CountDownLatch mail1HandedOff = new CountDownLatch(1);
        CountDownLatch mail1Checked = new CountDownLatch(1);
        AtomicBoolean handOffReturned = new AtomicBoolean();
        AtomicReference<Thread> pollThread = new AtomicReference<>();
        AtomicReference<Future<Integer>> pollRef = new AtomicReference<>();
        Processor handOff = exchange -> {
            String subject = exchange.getIn().getHeader("Subject", String.class);
            queued.put(subject, exchange);
            if ("Mail 1".equals(subject)) {
                keyOfMail1.set(exchange.getProperty(MailConsumer.MAIL_MESSAGE_UID, String.class));
                pollThread.set(Thread.currentThread());
                mail1HandedOff.countDown();
                // the poll goes on once the other thread checked the mail
                assertTrue(mail1Checked.await(20, TimeUnit.SECONDS));
                handOffReturned.set(true);
            }
        };
        MailConsumer consumer = new MailConsumer(endpoint(options), handOff, sender()) {
            @Override
            protected void processCommit(Message mail, Exchange exchange) {
                if (exchange == queued.get("Mail 1")) {
                    mail1Checked.countDown();
                    await().atMost(20, TimeUnit.SECONDS).until(() -> pollRef.get().isDone()
                            || handOffReturned.get() && isParked(pollThread.get()));
                    if (pollRef.get().isDone() && beforeMark != null) {
                        try {
                            assertTrue(beforeMark.await(20, TimeUnit.SECONDS));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                    }
                }
                super.processCommit(mail, exchange);
            }
        };
        start(consumer);
        context.addService(consumer);
        pollRef.set(threads.submit(consumer::poll));
        completion.set(threads.submit(() -> {
            assertTrue(mail1HandedOff.await(20, TimeUnit.SECONDS));
            // the exchange of "Mail 1" completed successfully: its synchronizations run (as ConsumerTemplate.doneUoW)
            complete(queued.get("Mail 1"));
            return null;
        }));
        return pollRef.get();
    }

    private static boolean isParked(Thread thread) {
        return thread.getState() == Thread.State.WAITING && LockSupport.getBlocker(thread) != null;
    }

    private MailEndpoint endpoint(String options) {
        return context.getEndpoint(kai.uriPrefix(Protocol.imap) + "&maxMessagesPerPoll=2" + options, MailEndpoint.class);
    }

    private Processor handOff() {
        return exchange -> queued.put(exchange.getIn().getHeader("Subject", String.class), exchange);
    }

    private JavaMailSender sender() {
        MailEndpoint endpoint = endpoint("");
        JavaMailSender sender = endpoint.getConfiguration().createJavaMailSender(context);
        endpoint.getConfiguration().configureJavaMailSender(context, sender);
        return sender;
    }

    private static void start(MailConsumer consumer) {
        consumer.setMaxMessagesPerPoll(2);
        consumer.setStartScheduler(false);
        consumer.start();
    }

    private static void complete(Exchange exchange) {
        UnitOfWorkHelper.doneSynchronizations(exchange, exchange.getExchangeExtension().handoverCompletions());
    }

    private static void removeByAnotherClient(String subject) throws MessagingException {
        Store store = openStore();
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_WRITE);
        for (Message message : folder.getMessages()) {
            if (subject.equals(message.getSubject())) {
                message.setFlag(Flags.Flag.DELETED, true);
            }
        }
        folder.close(true);
        store.close();
    }

    private static boolean isSeen(String subject) throws MessagingException {
        Store store = openStore();
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_ONLY);
        boolean seen = false;
        for (Message message : folder.getMessages()) {
            if (subject.equals(message.getSubject())) {
                seen = message.isSet(Flags.Flag.SEEN);
            }
        }
        folder.close(false);
        store.close();
        return seen;
    }

    private static Store openStore() throws MessagingException {
        Store store = new DefaultJavaMailSender().getSession().getStore("imap");
        store.connect("localhost", Mailbox.getPort(Protocol.imap), kai.getLogin(), kai.getPassword());
        return store;
    }
}
