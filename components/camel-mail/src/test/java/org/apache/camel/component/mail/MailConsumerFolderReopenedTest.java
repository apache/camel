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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * When the connection of the IMAP folder drops while a mail of a poll is routed, the consumer opens the folder again to
 * mark the mail. The mail objects of the poll belong to the earlier open of the folder: their message numbers are those
 * of the mailbox at that time. When mails were expunged meanwhile (by another client, or by the server), the message
 * number designates another mail in the new open, which was marked instead: a mail that was not routed was marked as
 * seen (or deleted) and never consumed.
 */
class MailConsumerFolderReopenedTest extends CamelTestSupport {
    private static final MailboxUser jane = Mailbox.getOrCreateUser("MailConsumerFolderReopenedTest-jane", "secret");

    private final DroppingProxy proxy = new DroppingProxy(Mailbox.getPort(Protocol.imap));
    private final AtomicBoolean dropOnSecond = new AtomicBoolean(true);

    @Override
    public void setupResources() throws Exception {
        Mailbox.clearAll(jane);
        proxy.start();
    }

    @AfterEach
    void stopProxy() throws IOException {
        proxy.close();
    }

    @Test
    void mailNotRoutedIsNotMarkedWhenTheFolderIsOpenedAgain() throws Exception {
        appendMessages(createMessages(3));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Message 0", "Message 1", "Message 2");
        context.getRouteController().startRoute("foo");
        mock.setResultWaitTime(TimeUnit.SECONDS.toMillis(15));
        mock.assertIsSatisfied();

        assertEquals(0, countUnseen(), "every mail is consumed");
    }

    private Message[] createMessages(int count) throws Exception {
        JavaMailSender sender = new DefaultJavaMailSender();
        Message[] messages = new Message[count];
        for (int i = 0; i < count; i++) {
            messages[i] = new MimeMessage(sender.getSession());
            messages[i].setText("Message " + i);
            messages[i].setSubject("Mail " + i);
        }
        return messages;
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

    /** Another mail client removes the first mail (already consumed): it is expunged from the mailbox. */
    private static void removeFirstMailByAnotherClient() throws Exception {
        Store store = openStore();
        try {
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_WRITE);
            for (Message message : folder.getMessages()) {
                if ("Mail 0".equals(message.getSubject())) {
                    message.setFlag(Flags.Flag.DELETED, true);
                }
            }
            folder.close(true);
        } finally {
            store.close();
        }
    }

    private static int countUnseen() throws Exception {
        Store store = openStore();
        try {
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);
            int unseen = 0;
            for (Message message : folder.getMessages()) {
                if (!message.isSet(Flags.Flag.SEEN)) {
                    unseen++;
                }
            }
            folder.close(false);
            return unseen;
        } finally {
            store.close();
        }
    }

    private static Store openStore() throws Exception {
        Store store = new DefaultJavaMailSender().getSession().getStore("imap");
        store.connect("localhost", Mailbox.getPort(Protocol.imap), jane.getLogin(), jane.getPassword());
        return store;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // through the proxy, which drops the connections of the consumer; two mails per poll
                from("imap://" + jane.getLogin() + "@localhost:" + proxy.getPort() + "?password=" + jane.getPassword()
                     + "&maxMessagesPerPoll=2&initialDelay=100&delay=500")
                        .routeId("foo").autoStartup(false)
                        .process(this::dropOnSecond)
                        .to("mock:result");
            }

            private void dropOnSecond(Exchange exchange) throws Exception {
                if ("Message 1".equals(exchange.getIn().getBody(String.class)) && dropOnSecond.compareAndSet(true, false)) {
                    // while the second mail is routed: another client removes the first one, and the connection
                    // of the consumer drops (an idle timeout of the server, a network failure)
                    removeFirstMailByAnotherClient();
                    proxy.dropConnections();
                    // the IMAP client probes the connection of an open folder when it was idle for more than a second
                    Awaitility.await().pollDelay(1500, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                            .until(() -> true);
                }
            }
        };
    }

    /** A TCP proxy in front of the IMAP server, whose connections can be dropped. */
    static final class DroppingProxy implements AutoCloseable {
        private final int target;
        private final List<Socket> sockets = new CopyOnWriteArrayList<>();
        private ServerSocket server;
        private volatile boolean running;

        DroppingProxy(int target) {
            this.target = target;
        }

        void start() throws IOException {
            server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            running = true;
            Thread acceptor = new Thread(this::accept, "imap-proxy-accept");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        int getPort() {
            return server.getLocalPort();
        }

        private void accept() {
            while (running) {
                try {
                    Socket client = server.accept();
                    Socket upstream = new Socket(InetAddress.getLoopbackAddress(), target);
                    sockets.add(client);
                    sockets.add(upstream);
                    pump(client, upstream);
                    pump(upstream, client);
                } catch (IOException e) {
                    // closed
                }
            }
        }

        private void pump(Socket from, Socket to) {
            Thread t = new Thread(() -> {
                byte[] buffer = new byte[8192];
                try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                        out.flush();
                    }
                } catch (IOException e) {
                    // dropped
                } finally {
                    closeQuietly(from);
                    closeQuietly(to);
                }
            }, "imap-proxy-pump");
            t.setDaemon(true);
            t.start();
        }

        void dropConnections() {
            for (Socket s : sockets) {
                closeQuietly(s);
            }
            sockets.clear();
        }

        @Override
        public void close() throws IOException {
            running = false;
            dropConnections();
            if (server != null) {
                server.close();
            }
        }

        private static void closeQuietly(Socket s) {
            try {
                s.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
