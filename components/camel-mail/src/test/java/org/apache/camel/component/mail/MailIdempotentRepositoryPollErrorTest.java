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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;

import org.apache.camel.Exchange;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * When a poll fails in the middle (routing an exchange throws, or the idempotent repository fails to add the key of a
 * later mail), the keys of the mails that the poll added but did not route must be removed from the idempotent
 * repository, or every later poll skips these mails as duplicates.
 */
class MailIdempotentRepositoryPollErrorTest extends CamelTestSupport {
    private static final MailboxUser james = Mailbox.getOrCreateUser("MailIdempotentRepositoryPollErrorTest-james", "secret");

    private final FailingIdempotentRepository repo = new FailingIdempotentRepository();
    private final List<Throwable> pollErrors = new ArrayList<>();

    @Override
    public void setupResources() throws Exception {
        Mailbox.clearAll(james);
        repo.start();
    }

    @Test
    void keysOfTheRestOfTheBatchAreRemovedWhenRoutingAnExchangeThrows() throws Exception {
        appendMessages(5);

        List<String> routed = new ArrayList<>();
        MailConsumer consumer = createConsumer(exchange -> {
            routed.add(exchange.getProperty(MailConsumer.MAIL_MESSAGE_UID, String.class));
            if (routed.size() == 2) {
                throw new RejectedExecutionException("Simulated: cannot route the exchange");
            }
        });
        try {
            consumer.poll();
        } finally {
            consumer.stop();
        }

        assertEquals(1, pollErrors.size());
        assertInstanceOf(RejectedExecutionException.class, pollErrors.get(0));
        assertEquals(2, routed.size());
        // the keys of the two mails that went into routing stay, the keys of the three mails after them are removed
        assertEquals(new TreeSet<>(routed), repo.keys());
    }

    @Test
    void keysOfTheMailsRetrievedSoFarAreRemovedWhenAddingTheKeyOfALaterMailFails() throws Exception {
        appendMessages(5);
        repo.failOnAdd.set(3);

        List<String> routed = new ArrayList<>();
        MailConsumer consumer
                = createConsumer(exchange -> routed.add(exchange.getProperty(MailConsumer.MAIL_MESSAGE_UID, String.class)));
        try {
            consumer.poll();
        } finally {
            consumer.stop();
        }

        assertEquals(1, pollErrors.size());
        assertInstanceOf(IllegalStateException.class, pollErrors.get(0));
        assertEquals(List.of(), routed, "The poll fails before any mail is routed");
        assertEquals(new TreeSet<>(), repo.keys(), "The keys of the mails retrieved before the failure must be removed");
    }

    /**
     * A consumer whose {@link MailConsumer#processExchange(Exchange)} calls the given action instead of a route, polled
     * directly by the test (the scheduler is not started).
     */
    private MailConsumer createConsumer(RouteAction action) throws Exception {
        context.getRegistry().bind("pollErrorRepo", repo);
        MailEndpoint endpoint = context.getEndpoint(
                james.uriPrefix(Protocol.imap) + "&idempotentRepository=#pollErrorRepo", MailEndpoint.class);
        JavaMailSender sender = endpoint.getConfiguration().createJavaMailSender(context);
        endpoint.getConfiguration().configureJavaMailSender(context, sender);
        MailConsumer consumer = new MailConsumer(endpoint, exchange -> {
        }, sender) {
            @Override
            protected void processExchange(Exchange exchange) throws Exception {
                action.route(exchange);
            }
        };
        consumer.setStartScheduler(false);
        consumer.setExceptionHandler(new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                pollErrors.add(exception);
            }

            @Override
            public void handleException(String message, Throwable exception) {
                pollErrors.add(exception);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                pollErrors.add(exception);
            }
        });
        consumer.start();
        return consumer;
    }

    private static void appendMessages(int count) throws Exception {
        JavaMailSender sender = new DefaultJavaMailSender();
        Message[] messages = new Message[count];
        for (int i = 0; i < count; i++) {
            messages[i] = new MimeMessage(sender.getSession());
            messages[i].setText("Message " + i);
        }
        Store store = sender.getSession().getStore("imap");
        store.connect("localhost", Mailbox.getPort(Protocol.imap), james.getLogin(), james.getPassword());
        try {
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_WRITE);
            folder.appendMessages(messages);
            folder.close(true);
        } finally {
            store.close();
        }
    }

    @FunctionalInterface
    interface RouteAction {
        void route(Exchange exchange) throws Exception;
    }

    /**
     * A memory repository whose n-th {@code add} fails, as a JDBC/Redis repository that is briefly unavailable.
     */
    static final class FailingIdempotentRepository extends MemoryIdempotentRepository {
        final AtomicInteger failOnAdd = new AtomicInteger();
        private final AtomicInteger adds = new AtomicInteger();

        @Override
        public boolean add(String key) {
            if (adds.incrementAndGet() == failOnAdd.get()) {
                throw new IllegalStateException("Simulated: the repository is not available");
            }
            return super.add(key);
        }

        Set<String> keys() {
            return new TreeSet<>(getCache().keySet());
        }
    }
}
