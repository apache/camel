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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.KeyValueIdempotentRepository;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two consumers share an idempotent repository to consume one mailbox. Consumer B searches the unseen mails, then
 * consumer A consumes the mail, marks it SEEN and removes its key (idempotentRepositoryRemoveOnCommit=true, the
 * default); then B adds the key, which succeeds. B must not route the mail again. The gate is B's mailUidGenerator,
 * which runs between the search and the add.
 */
class MailSharedIdempotentRepositoryTest extends CamelTestSupport {
    private static final MailboxUser lee = Mailbox.getOrCreateUser("MailSharedIdempotentRepositoryTest-lee", "secret");

    private final CountDownLatch bSearched = new CountDownLatch(1);
    private final CountDownLatch bPolled = new CountDownLatch(1);
    private final AtomicReference<String> keyOfB = new AtomicReference<>();

    @BindToRegistry("repo")
    private final KeyValueIdempotentRepository repo = new KeyValueIdempotentRepository();

    @BindToRegistry("gateB")
    private final MailUidGenerator gateB = (endpoint, message) -> {
        String key = new DefaultMailUidGenerator().generateUuid(endpoint, message);
        keyOfB.set(key);
        bSearched.countDown();
        // wait until A routed the mail, marked it SEEN and removed its key
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> !repo.contains(key) && lee.getInbox().getNewMessageCount() == 0
                        && getMockEndpoint("mock:result").getReceivedCounter() == 1);
        return key;
    };

    // called by B at the end of its poll, after its batch was routed
    @BindToRegistry("afterPollB")
    private final MailBoxPostProcessAction afterPollB = folder -> bPolled.countDown();

    @Override
    public void setupResources() throws Exception {
        Mailbox.clearAll(lee);
        JavaMailSender sender = new DefaultJavaMailSender();
        Store store = sender.getSession().getStore("imap");
        store.connect("localhost", Mailbox.getPort(Protocol.imap), lee.getLogin(), lee.getPassword());
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_WRITE);
        Message message = new MimeMessage(sender.getSession());
        message.setText("Only mail");
        folder.appendMessages(new Message[] { message });
        folder.close(true);
        store.close();
    }

    @Test
    void mailConsumedByAnotherConsumerAfterTheSearchIsNotRoutedAgain() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        context.getRouteController().startRoute("B");
        assertTrue(bSearched.await(20, TimeUnit.SECONDS));
        // B searched the unseen mails and waits before it adds the key; A consumes the mail now
        context.getRouteController().startRoute("A");
        assertTrue(bPolled.await(20, TimeUnit.SECONDS));

        MockEndpoint.assertIsSatisfied(context);
        List<Object> consumers = mock.getExchanges().stream().map(e -> e.getIn().getHeader("consumer")).toList();
        assertEquals(List.of("A"), consumers, "only A routes the mail");
        assertFalse(repo.contains(keyOfB.get()), "B removed the key it added again");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                String base = lee.uriPrefix(Protocol.imap) + "&idempotentRepository=#repo&initialDelay=0&delay=60000";
                from(base).routeId("A").autoStartup(false)
                        .setHeader("consumer", constant("A"))
                        .to("mock:result");
                from(base + "&mailUidGenerator=#gateB&postProcessAction=#afterPollB").routeId("B").autoStartup(false)
                        .setHeader("consumer", constant("B"))
                        .to("mock:result");
            }
        };
    }
}
