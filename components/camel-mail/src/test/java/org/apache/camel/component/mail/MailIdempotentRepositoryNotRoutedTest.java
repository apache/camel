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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.mail.Flags;
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
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mails of a poll that are not routed (the consumer stops in the middle of the batch, or the poll fails while the
 * mails are retrieved) must not stay in the idempotent repository, as they would then be skipped as duplicates by every
 * later poll, also of another consumer that shares the repository.
 */
class MailIdempotentRepositoryNotRoutedTest extends CamelTestSupport {
    private static final MailboxUser jones = Mailbox.getOrCreateUser("MailIdempotentRepositoryNotRoutedTest-jones", "secret");

    @BindToRegistry("myRepo")
    private final MemoryIdempotentRepository myRepo = new MemoryIdempotentRepository();

    @BindToRegistry("myHandler")
    private final PollErrorHandler pollErrorHandler = new PollErrorHandler();

    private final AtomicBoolean stopOnFirst = new AtomicBoolean();

    @Override
    public void setupResources() throws Exception {
        myRepo.start();
        Mailbox.clearAll(jones);
    }

    @Test
    void mailsOfABatchCutShortByAStopAreConsumedAfterTheRestart() throws Exception {
        appendMessages(createMessages(5));
        stopOnFirst.set(true);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        context.getRouteController().startRoute("foo");
        MockEndpoint.assertIsSatisfied(context);

        // the route was stopped while it routed the first mail: the other four were not routed
        Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .until(() -> context.getRouteController().getRouteStatus("foo").isStopped());
        assertEquals(1, mock.getReceivedCounter());
        assertEquals(0, myRepo.getCacheSize(), "The keys of the mails that were not routed must be removed");

        mock.reset();
        mock.expectedMessageCount(4);
        context.getRouteController().startRoute("foo");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void mailsRetrievedBeforeAFailingMailAreConsumedOnceItIsRemoved() throws Exception {
        Message[] good = createMessages(3);
        appendMessages(good[0], good[1], good[2], createMalformedMessage());

        context.getRouteController().startRoute("foo");
        // the poll fails on the malformed mail (default skipFailedMessage=false), so no mail of it is routed
        assertTrue(pollErrorHandler.failed.await(10, TimeUnit.SECONDS), "The poll should fail on the malformed mail");
        context.getRouteController().stopRoute("foo");
        // a poll that was running when the route stopped may still be retrieving mails
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(0, myRepo.getCacheSize(), "The keys of the mails that were not routed must be removed"));

        // the operator removes the malformed mail, the others must now be consumed
        removeMalformedMessage();
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);
        context.getRouteController().startRoute("foo");
        MockEndpoint.assertIsSatisfied(context);
    }

    private Message[] createMessages(int count) throws Exception {
        JavaMailSender sender = new DefaultJavaMailSender();
        Message[] messages = new Message[count];
        for (int i = 0; i < count; i++) {
            messages[i] = new MimeMessage(sender.getSession());
            messages[i].setText("Message " + i);
            messages[i].setHeader("Message-ID", "myuid-" + i);
        }
        return messages;
    }

    private static Message createMalformedMessage() throws Exception {
        // a multipart mail whose body does not contain its boundary: reading its parts fails
        String raw = "Message-ID: malformed\r\n"
                     + "Subject: malformed\r\n"
                     + "MIME-Version: 1.0\r\n"
                     + "Content-Type: multipart/mixed; boundary=\"the-boundary\"\r\n"
                     + "\r\n"
                     + "no parts here\r\n";
        return new MimeMessage(
                new DefaultJavaMailSender().getSession(), new ByteArrayInputStream(raw.getBytes(StandardCharsets.US_ASCII)));
    }

    private static void appendMessages(Message... messages) throws Exception {
        Store store = openStore();
        try {
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_WRITE);
            folder.appendMessages(messages);
            folder.close(true);
        } finally {
            store.close();
        }
    }

    private static void removeMalformedMessage() throws Exception {
        Store store = openStore();
        try {
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_WRITE);
            for (Message message : folder.getMessages()) {
                if ("malformed".equals(message.getSubject())) {
                    message.setFlag(Flags.Flag.DELETED, true);
                }
            }
            folder.close(true);
        } finally {
            store.close();
        }
    }

    private static Store openStore() throws Exception {
        Store store = new DefaultJavaMailSender().getSession().getStore("imap");
        store.connect("localhost", Mailbox.getPort(Protocol.imap), jones.getLogin(), jones.getPassword());
        return store;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(jones.uriPrefix(Protocol.imap)
                     + "&idempotentRepository=#myRepo&exceptionHandler=#myHandler&initialDelay=100&delay=100")
                        .routeId("foo").autoStartup(false)
                        .process(this::stopOnFirst)
                        .to("mock:result");
            }

            private void stopOnFirst(Exchange exchange) {
                if (stopOnFirst.compareAndSet(true, false)) {
                    MailConsumer consumer = (MailConsumer) exchange.getContext().getRoute("foo").getConsumer();
                    // a graceful stop of the route while it routes the first mail of the batch: the stop waits for
                    // this exchange, and the consumer routes no further mail of the batch
                    CompletableFuture.runAsync(() -> {
                        try {
                            exchange.getContext().getRouteController().stopRoute("foo");
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    });
                    Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> !consumer.isBatchAllowed());
                }
            }
        };
    }

    static final class PollErrorHandler implements ExceptionHandler {
        final CountDownLatch failed = new CountDownLatch(1);

        @Override
        public void handleException(Throwable exception) {
            failed.countDown();
        }

        @Override
        public void handleException(String message, Throwable exception) {
            failed.countDown();
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            failed.countDown();
        }
    }
}
