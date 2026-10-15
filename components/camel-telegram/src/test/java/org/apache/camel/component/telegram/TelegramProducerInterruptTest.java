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
package org.apache.camel.component.telegram;

import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import org.apache.camel.AsyncProducer;
import org.apache.camel.Exchange;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A caller that is interrupted while it waits for the answer of Telegram: the exchange fails once and the request is
 * given up, so that nothing is left waiting for an answer that nobody reads.
 */
class TelegramProducerInterruptTest extends CamelTestSupport {

    private static final String OPTIONS = "telegram:bots?authorizationToken=mock-token&chatId=1";

    @Test
    void interruptWhileWaitingForTheAnswerClosesTheConnection() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            CountDownLatch requestArrived = new CountDownLatch(1);
            CompletableFuture<String> connection = new CompletableFuture<>();
            Thread telegram = new Thread(() -> neverAnswer(server, requestArrived, connection), "telegram-never-answers");
            telegram.start();

            Sent sent = sendAndInterrupt(OPTIONS + "&baseUri=http://localhost:" + server.getLocalPort(), requestArrived);

            assertInterruptedOnce(sent);
            // the request is aborted: the client does not keep the connection for an answer that nobody reads
            assertThat(connection.get(20, TimeUnit.SECONDS)).isEqualTo("closed by the client");
            telegram.join(TimeUnit.SECONDS.toMillis(20));
            assertThat(telegram.isAlive()).as("the server thread still runs").isFalse();
        }
    }

    @Test
    void interruptCancelsTheFutureOfACustomClient() throws Exception {
        // the futures of this client are plain ones: cancelling a dependent stage does not reach them
        PlainFutureClient client = new PlainFutureClient();
        context.getRegistry().bind("plainFutureClient", client);

        Sent sent = sendAndInterrupt(OPTIONS + "&baseUri=http://localhost:1&client=#plainFutureClient", client.requested);

        assertInterruptedOnce(sent);
        assertThat(client.future.get().isCancelled()).as("future that sendAsync returned is cancelled").isTrue();
    }

    /**
     * Sends a text message from another thread, interrupts that thread when the request is on its way, and returns when
     * the producer has returned.
     */
    private Sent sendAndInterrupt(String uri, CountDownLatch requestArrived) throws Exception {
        AsyncProducer producer = context.getEndpoint(uri).createAsyncProducer();
        producer.start();
        try {
            Exchange exchange = producer.getEndpoint().createExchange();
            OutgoingTextMessage message = new OutgoingTextMessage();
            message.setText("hello");
            exchange.getIn().setBody(message);

            Sent sent = new Sent(exchange);
            Thread caller = new Thread(() -> {
                try {
                    producer.process(exchange, doneSync -> sent.calls.incrementAndGet());
                } catch (RuntimeException e) {
                    sent.thrownByProcess.set(e);
                }
                sent.interruptFlagKept.set(Thread.currentThread().isInterrupted());
            }, "telegram-caller");
            caller.start();

            assertThat(requestArrived.await(20, TimeUnit.SECONDS)).as("the request was sent within 20 seconds").isTrue();
            caller.interrupt();
            caller.join(TimeUnit.SECONDS.toMillis(20));
            assertThat(caller.isAlive()).as("the producer still waits after the interrupt").isFalse();
            return sent;
        } finally {
            producer.stop();
        }
    }

    private static void assertInterruptedOnce(Sent sent) {
        assertThat(sent.thrownByProcess.get()).as("thrown by process() of the producer").isNull();
        assertThat(sent.calls.get()).as("calls of the callback").isEqualTo(1);
        assertThat(sent.interruptFlagKept.get()).as("interrupt flag kept").isTrue();
        assertThat(sent.exchange.getException()).isExactlyInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(InterruptedException.class);
    }

    /**
     * Reads the request and then waits, without an answer, for what the client does with the connection.
     */
    private static void neverAnswer(ServerSocket server, CountDownLatch requestArrived, CompletableFuture<String> connection) {
        try (Socket socket = server.accept()) {
            socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(20));
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[8192];
            int read = in.read(buffer);
            requestArrived.countDown();
            // the client sends nothing more after the request; the cap only bounds the loop
            for (int reads = 0; read != -1 && reads < 1000; reads++) {
                read = in.read(buffer);
            }
            connection.complete(read == -1 ? "closed by the client" : "still sending after 1000 reads");
        } catch (SocketTimeoutException e) {
            connection.complete("still open after 20 seconds");
        } catch (IOException e) {
            // a reset is also the client giving up the connection
            connection.complete("closed by the client");
        }
    }

    private static final class Sent {
        private final Exchange exchange;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<RuntimeException> thrownByProcess = new AtomicReference<>();
        private final AtomicReference<Boolean> interruptFlagKept = new AtomicReference<>();

        private Sent(Exchange exchange) {
            this.exchange = exchange;
        }
    }

    /**
     * A client that never answers and returns a plain {@link CompletableFuture}, as a client other than the one of the
     * JDK may do.
     */
    private static final class PlainFutureClient extends HttpClient {
        private final CountDownLatch requested = new CountDownLatch(1);
        private final AtomicReference<CompletableFuture<?>> future = new AtomicReference<>();

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            CompletableFuture<HttpResponse<T>> answer = new CompletableFuture<>();
            future.set(answer);
            requested.countDown();
            return answer;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }
    }
}
