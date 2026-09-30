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
package org.apache.camel.component.typesafeai;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class TypeSafeAiCancellationTest extends TypeSafeAiTestSupport {
    enum Cancellation {
        Timeout,
        Interrupt,
        Stop
    }

    @ParameterizedTest
    @EnumSource(Cancellation.class)
    void abortsStalledBodyAndAllowsEndpointShutdown(Cancellation cancellation) throws Exception {
        var headersSent = new CountDownLatch(1);
        var tasks = Executors.newFixedThreadPool(2);
        var caller = new AtomicReference<Thread>();
        var interrupted = new AtomicBoolean();
        try (var socketServer = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            socketServer.setSoTimeout(5000);
            var disconnected = tasks.submit(() -> {
                try (var socket = socketServer.accept()) {
                    socket.setSoTimeout(10000);
                    var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    int length = 0;
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                            length = Integer.parseInt(line.substring(15).trim());
                        }
                    }
                    for (int i = 0; i < length; i++) {
                        assertThat(reader.read()).isNotEqualTo(-1);
                    }
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
                                                    + "Content-Length: 100\r\n\r\n{")
                            .getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().flush();
                    headersSent.countDown();
                    return reader.read();
                }
            });
            TypeSafeAiEndpoint endpoint = context.getEndpoint("typesafe-ai:cancel?baseUrl=http://127.0.0.1:"
                                                              + socketServer.getLocalPort() + "&requestTimeout="
                                                              + (cancellation == Cancellation.Timeout ? 1500 : 30000),
                    TypeSafeAiEndpoint.class);
            endpoint.start();
            var outcome = tasks.submit(() -> {
                caller.set(Thread.currentThread());
                try {
                    endpoint.evaluate(request("test"));
                    return null;
                } catch (Exception e) {
                    interrupted.set(Thread.currentThread().isInterrupted());
                    return e;
                }
            });
            assertThat(headersSent.await(5, TimeUnit.SECONDS)).isTrue();
            long stopped = System.nanoTime();
            if (cancellation == Cancellation.Interrupt) {
                caller.get().interrupt();
            } else if (cancellation == Cancellation.Stop) {
                endpoint.stop();
            }
            Exception failure = outcome.get(5, TimeUnit.SECONDS);
            if (cancellation == Cancellation.Timeout) {
                assertThat(failure).isInstanceOf(TimeoutException.class);
            } else if (cancellation == Cancellation.Interrupt) {
                assertThat(failure).isInstanceOf(InterruptedException.class);
                assertThat(interrupted).isTrue();
            } else {
                assertThat(failure).isInstanceOf(CancellationException.class);
            }
            assertThat(disconnected.get(5, TimeUnit.SECONDS)).isEqualTo(-1);
            endpoint.stop();
            assertThat(endpoint.isStopped()).isTrue();
            assertThat(Duration.ofNanos(System.nanoTime() - stopped)).isLessThan(Duration.ofSeconds(5));
        } finally {
            tasks.shutdownNow();
        }
    }
}
