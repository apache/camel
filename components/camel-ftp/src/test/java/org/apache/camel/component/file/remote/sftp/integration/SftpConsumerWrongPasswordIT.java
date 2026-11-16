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
package org.apache.camel.component.file.remote.sftp.integration;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.remote.RemoteFileConsumerWarnCapture;
import org.apache.camel.component.file.remote.RemoteFilePollingConsumerPollStrategy;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.ftp.services.embedded.SftpEmbeddedService;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.sshd.server.auth.UserAuthFactory;
import org.apache.sshd.server.auth.keyboard.UserAuthKeyboardInteractiveFactory;
import org.apache.sshd.server.auth.password.UserAuthPasswordFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With a wrong password, the sftp consumer sends the password once per poll: once per session, whether the server asks
 * for it with the password or the keyboard-interactive method, and it does not open another session when the server
 * rejected the login, as each rejected login counts towards the lockout policy of the server. Other connection failures
 * are retried as before. The WARN of the consumer says why it cannot connect.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
class SftpConsumerWrongPasswordIT extends CamelTestSupport {

    @RegisterExtension
    CountingSftpEmbeddedService service = new CountingSftpEmbeddedService();

    private final RemoteFileConsumerWarnCapture warnings = new RemoteFileConsumerWarnCapture();

    @BeforeEach
    void captureWarnings() {
        warnings.attach();
    }

    @AfterEach
    void releaseWarnings() {
        warnings.detach();
    }

    @ParameterizedTest
    @ValueSource(strings = { "password", "keyboard-interactive" })
    void wrongPasswordIsSentOncePerPoll(String method) throws Exception {
        service.allowOnly(method);

        PollRecorder polls = consume("wrong", "&autoCreate=false", service::passwordChecks);

        await().atMost(30, TimeUnit.SECONDS).until(() -> polls.countAtPoll.size() >= 2);
        // the passwords the server checked by the end of the first and the second poll
        assertEquals(List.of(1, 2), polls.countAtPoll.subList(0, 2));

        // one WARN, until the consumer connects, which says that the login failed
        assertEquals(List.of("Cannot connect/login to: sftp://admin@localhost:" + service.getPort()
                             + " due to: Auth fail for user 'admin': the server did not accept the password."
                             + " Will skip this poll."),
                warnings.messages());
    }

    @Test
    void autoCreateSendsTheWrongPasswordOnceBeforeTheFirstPoll() throws Exception {
        service.allowOnly("password");

        // the consumer connects once when it starts to create the starting directory, and again for the first poll
        PollRecorder polls = consume("wrong", "", service::passwordChecks);

        await().atMost(30, TimeUnit.SECONDS).until(() -> !polls.countAtPoll.isEmpty());
        assertEquals(2, polls.countAtPoll.get(0));
    }

    @ParameterizedTest
    @ValueSource(strings = { "password", "keyboard-interactive" })
    void rightPasswordLogsIn(String method) throws Exception {
        service.allowOnly(method);
        Path dir = service.getFtpRootDir().resolve("wrongpassword");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("hello.txt"), "Hello World");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        consume(CountingSftpEmbeddedService.PASSWORD, "", service::passwordChecks);

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(1, service.passwordChecks());
        assertTrue(warnings.messages().isEmpty(), "No WARN: " + warnings.messages());
    }

    @Test
    void connectionFailureIsRetried() throws Exception {
        try (ClosingServer server = new ClosingServer()) {
            PollRecorder polls = new PollRecorder(server::connections);
            context.getRegistry().bind("polls", polls);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("sftp://localhost:" + server.getPort() + "/wrongpassword?username=admin&password=wrong"
                         + "&useUserKnownHostsFile=false&autoCreate=false&reconnectDelay=10"
                         + "&initialDelay=0&delay=1000&pollStrategy=#polls")
                            .to("mock:result");
                }
            });

            await().atMost(30, TimeUnit.SECONDS).until(() -> polls.countAtPoll.size() >= 2);
            // the connections the server accepted by the end of the first and the second poll: maximumReconnectAttempts
            // (3) per poll
            assertEquals(List.of(3, 6), polls.countAtPoll.subList(0, 2));

            assertEquals(List.of("Cannot connect/login to: sftp://admin@localhost:" + server.getPort()
                                 + " due to: connection is closed by foreign host. Will skip this poll."),
                    warnings.messages());
        }
    }

    private PollRecorder consume(String password, String options, IntSupplier counter) throws Exception {
        PollRecorder polls = new PollRecorder(counter);
        context.getRegistry().bind("polls", polls);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}/wrongpassword?username=admin&password="
                     + password + "&knownHostsFile=" + service.getKnownHostsFile()
                     + "&initialDelay=0&delay=1000&pollStrategy=#polls" + options)
                        .to("mock:result");
            }
        });
        return polls;
    }

    /**
     * The embedded server, which accepts the password {@link #PASSWORD} only, with the authentication methods a test
     * chooses, and counts the passwords it checks.
     */
    static class CountingSftpEmbeddedService extends SftpEmbeddedService {

        static final String PASSWORD = "admin";

        private final AtomicInteger passwordChecks = new AtomicInteger();

        @Override
        public void setUpServer() throws Exception {
            super.setUpServer();
            // keyboard-interactive checks the answer with the same authenticator
            sshd.setPasswordAuthenticator((username, password, session) -> {
                passwordChecks.incrementAndGet();
                return PASSWORD.equals(password);
            });
        }

        void allowOnly(String method) {
            UserAuthFactory factory = "password".equals(method)
                    ? UserAuthPasswordFactory.INSTANCE : UserAuthKeyboardInteractiveFactory.INSTANCE;
            sshd.setUserAuthFactories(List.of(factory));
        }

        int passwordChecks() {
            return passwordChecks.get();
        }
    }

    /**
     * Records, at the end of each poll, the value of a counter of the server.
     */
    static class PollRecorder extends RemoteFilePollingConsumerPollStrategy {

        final List<Integer> countAtPoll = new CopyOnWriteArrayList<>();
        private final IntSupplier counter;

        PollRecorder(IntSupplier counter) {
            this.counter = counter;
        }

        @Override
        public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
            countAtPoll.add(counter.getAsInt());
            super.commit(consumer, endpoint, polledMessages);
        }

        @Override
        public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) throws Exception {
            countAtPoll.add(counter.getAsInt());
            return super.rollback(consumer, endpoint, retryCounter, e);
        }
    }

    /**
     * A server that closes every connection it accepts, before the SSH handshake.
     */
    static class ClosingServer implements AutoCloseable {

        private final ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        private final AtomicInteger connections = new AtomicInteger();
        private final Thread acceptor = new Thread(this::accept, "ClosingServer");

        ClosingServer() throws IOException {
            acceptor.setDaemon(true);
            acceptor.start();
        }

        private void accept() {
            while (!socket.isClosed()) {
                try (Socket ignored = socket.accept()) {
                    connections.incrementAndGet();
                } catch (IOException e) {
                    // closed
                }
            }
        }

        int getPort() {
            return socket.getLocalPort();
        }

        int connections() {
            return connections.get();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
