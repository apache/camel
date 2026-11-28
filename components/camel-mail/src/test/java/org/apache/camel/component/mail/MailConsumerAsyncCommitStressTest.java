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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.apache.camel.support.KeyValueIdempotentRepository;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Many pollEnrich calls on 4 threads: their exchanges complete on the caller threads while the poll of the pollEnrich
 * consumer connects, closes the folder or disconnects. No call hangs, every mail is routed once, and every mail is
 * marked.
 */
class MailConsumerAsyncCommitStressTest extends CamelTestSupport {
    private static final int MAILS = 20;
    private static final int THREADS = 4;
    private static final MailboxUser dan = Mailbox.getOrCreateUser("MailConsumerAsyncCommitStressTest-dan", "secret");
    private static final MailboxUser cat = Mailbox.getOrCreateUser("MailConsumerAsyncCommitStressTest-cat", "secret");
    private static final MailboxUser pat = Mailbox.getOrCreateUser("MailConsumerAsyncCommitStressTest-pat", "secret");

    @BindToRegistry("repo")
    private final KeyValueIdempotentRepository repo = new KeyValueIdempotentRepository();

    @Override
    public void setupResources() throws Exception {
        for (MailboxUser user : new MailboxUser[] { dan, cat, pat }) {
            Mailbox.clearAll(user);
            JavaMailSender sender = new DefaultJavaMailSender();
            Store store = sender.getSession().getStore("imap");
            store.connect("localhost", Mailbox.getPort(Protocol.imap), user.getLogin(), user.getPassword());
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_WRITE);
            Message[] messages = new Message[MAILS];
            for (int i = 0; i < MAILS; i++) {
                messages[i] = new MimeMessage(sender.getSession());
                messages[i].setSubject("Mail " + i);
                messages[i].setText("Message " + i);
            }
            folder.appendMessages(messages);
            folder.close(true);
            store.close();
        }
    }

    @Test
    void imapDisconnectAndDelete() throws Exception {
        enrichAllMailsConcurrently("direct:dan", "mock:dan");
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(0, dan.getInbox().getMessageCount()));
    }

    @Test
    void imapCloseFolderAndSeen() throws Exception {
        enrichAllMailsConcurrently("direct:cat", "mock:cat");
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(0, cat.getInbox().getNewMessageCount()));
    }

    @Test
    void pop3DisconnectAndDelete() throws Exception {
        enrichAllMailsConcurrently("direct:pat", "mock:pat");
        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(0, pat.getInbox().getMessageCount()));
    }

    private void enrichAllMailsConcurrently(String from, String to) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<?>> calls = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                calls.add(pool.submit(() -> {
                    for (int i = 0; i < MAILS / THREADS; i++) {
                        template.sendBody(from, "");
                    }
                }));
            }
            for (Future<?> call : calls) {
                // a TimeoutException fails the test: a pollEnrich call hangs
                call.get(90, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        Set<String> bodies = new HashSet<>();
        for (Exchange exchange : getMockEndpoint(to).getExchanges()) {
            String body = exchange.getIn().getBody(String.class);
            if (body != null && !body.isEmpty()) {
                assertTrue(bodies.add(body.trim()), "mail routed twice: " + body);
            }
        }
        assertEquals(MAILS, bodies.size(), "every mail is routed once");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                String options = "&idempotentRepository=#repo&initialDelay=0&delay=20";
                from("direct:dan").pollEnrich(dan.uriPrefix(Protocol.imap) + "&disconnect=true&delete=true" + options, 20000)
                        .to("mock:dan");
                from("direct:cat").pollEnrich(cat.uriPrefix(Protocol.imap) + "&closeFolder=true" + options, 20000)
                        .to("mock:cat");
                from("direct:pat").pollEnrich(pat.uriPrefix(Protocol.pop3) + "&disconnect=true&delete=true" + options, 20000)
                        .to("mock:pat");
            }
        };
    }
}
