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
package org.apache.camel.component.openfga;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.health.HealthCheck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

class OpenFgaHealthProbeDeadlineTest {

    @Test
    @Timeout(15)
    void reportsDownAndCancelsWhenTheHealthBodyStallsAfterHeaders() throws Exception {
        try (StalledHealthEndpoint server = new StalledHealthEndpoint();
             OpenFgaProducerHealthCheck check = new OpenFgaProducerHealthCheck(server.url(), null, "store", "endpoint", null)) {
            check.setEnabled(true);

            HealthCheck.Result result = check.call();

            assertThat(server.headersSent.getCount()).isZero();
            assertThat(result.getState()).isEqualTo(HealthCheck.State.DOWN);
            assertThat(result.getError()).isPresent().get().isInstanceOf(HttpTimeoutException.class);
            assertThat(server.disconnected.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @Timeout(10)
    void interruptionCancelsTheHealthRequestAndPreservesTheInterruptFlag() throws Exception {
        try (StalledHealthEndpoint server = new StalledHealthEndpoint();
             OpenFgaProducerHealthCheck check = new OpenFgaProducerHealthCheck(server.url(), null, "store", "endpoint", null);
             var executor = Executors.newSingleThreadExecutor()) {
            check.setEnabled(true);
            AtomicReference<Thread> checking = new AtomicReference<>();
            AtomicBoolean interruptPreserved = new AtomicBoolean();
            var result = executor.submit(() -> {
                checking.set(Thread.currentThread());
                HealthCheck.Result outcome = check.call();
                interruptPreserved.set(Thread.currentThread().isInterrupted());
                return outcome;
            });
            assertThat(server.headersSent.await(3, TimeUnit.SECONDS)).isTrue();

            checking.get().interrupt();

            HealthCheck.Result outcome = result.get(3, TimeUnit.SECONDS);
            assertThat(outcome.getState()).isEqualTo(HealthCheck.State.DOWN);
            assertThat(outcome.getError()).isPresent().get().isInstanceOf(InterruptedException.class);
            assertThat(interruptPreserved).isTrue();
            assertThat(server.disconnected.await(3, TimeUnit.SECONDS)).isTrue();
        }
    }

    /** Sends a partial HTTP body, then observes the peer closing the cancelled request. */
    private static final class StalledHealthEndpoint implements AutoCloseable {
        private final ServerSocket listener = new ServerSocket(0);
        private final CountDownLatch headersSent = new CountDownLatch(1);
        private final CountDownLatch disconnected = new CountDownLatch(1);
        private final Thread server;
        private volatile Socket peer;

        private StalledHealthEndpoint() throws IOException {
            server = new Thread(this::serve, "openfga-stalled-health");
            server.setDaemon(true);
            server.start();
        }

        private String url() {
            return "http://localhost:" + listener.getLocalPort();
        }

        private void serve() {
            try (Socket accepted = listener.accept()) {
                peer = accepted;
                BufferedReader request = new BufferedReader(
                        new InputStreamReader(accepted.getInputStream(), StandardCharsets.US_ASCII));
                String line;
                while ((line = request.readLine()) != null && !line.isEmpty()) {
                    // Consume the request headers before looking for cancellation on the connection.
                }
                accepted.getOutputStream().write(
                        ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 100\r\n\r\n{")
                                .getBytes(StandardCharsets.US_ASCII));
                accepted.getOutputStream().flush();
                headersSent.countDown();
                if (accepted.getInputStream().read() == -1) {
                    disconnected.countDown();
                }
            } catch (IOException e) {
                if (headersSent.getCount() == 0) {
                    // Cancellation may reset the connection instead of closing it cleanly.
                    disconnected.countDown();
                }
            }
        }

        @Override
        public void close() throws Exception {
            listener.close();
            Socket accepted = peer;
            if (accepted != null) {
                accepted.close();
            }
            server.join(TimeUnit.SECONDS.toMillis(3));
        }
    }
}
