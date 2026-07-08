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
package org.apache.camel.component.platform.http.vertx;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxPlatformHttpSseTest {

    @Test
    void testSseEventsDeliveredEagerly() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        // Writer waits on this latch after sending event-1.
        // If event-1 is flushed eagerly, the client reads it and counts down,
        // unblocking the writer to send event-2.
        // If events are batched, the client never sees event-1 until the stream
        // closes, so the latch times out and the test fails.
        CountDownLatch event1Received = new CountDownLatch(1);
        AtomicReference<Throwable> writerError = new AtomicReference<>();

        HttpClient client = null;
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/sse")
                            .process(exchange -> {
                                PipedOutputStream out = new PipedOutputStream();
                                PipedInputStream in = new PipedInputStream(out);

                                exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "text/event-stream");
                                exchange.getMessage().setBody(in);

                                Thread writer = new Thread(() -> {
                                    try {
                                        out.write("data: event-1\n\n".getBytes(StandardCharsets.UTF_8));
                                        out.flush();

                                        assertTrue(event1Received.await(5, TimeUnit.SECONDS),
                                                "Client did not receive event-1 before timeout — events may be buffered");

                                        out.write("data: event-2\n\n".getBytes(StandardCharsets.UTF_8));
                                        out.flush();
                                        out.close();
                                    } catch (Throwable e) {
                                        writerError.set(e);
                                        try {
                                            out.close();
                                        } catch (Exception ignored) {
                                        }
                                    }
                                });
                                writer.setDaemon(true);
                                writer.start();
                            });
                }
            });

            VertxPlatformHttpEngineTest.startCamelContext(context);

            VertxPlatformHttpServer server = context.hasService(VertxPlatformHttpServer.class);
            client = server.getVertx().createHttpClient();

            StringBuilder received = new StringBuilder();
            CompletableFuture<Void> streamDone = new CompletableFuture<>();

            client.request(HttpMethod.GET, server.getPort(), "localhost", "/sse")
                    .onSuccess(request -> {
                        request.response()
                                .onSuccess(response -> {
                                    assertEquals(200, response.statusCode());
                                    assertEquals("text/event-stream", response.getHeader("Content-Type"));

                                    response.handler(buffer -> {
                                        String chunk = buffer.toString(StandardCharsets.UTF_8);
                                        received.append(chunk);

                                        if (chunk.contains("event-1")) {
                                            event1Received.countDown();
                                        }
                                    });
                                    response.endHandler(v -> streamDone.complete(null));
                                    response.exceptionHandler(streamDone::completeExceptionally);
                                })
                                .onFailure(streamDone::completeExceptionally);
                        request.end();
                    })
                    .onFailure(streamDone::completeExceptionally);

            streamDone.get(10, TimeUnit.SECONDS);

            String[] events = received.toString().split("\n\n");
            assertEquals(2, events.length);
            assertEquals("data: event-1", events[0]);
            assertEquals("data: event-2", events[1]);

            Throwable error = writerError.get();
            if (error != null) {
                throw new AssertionError("Writer thread failed", error);
            }
        } finally {
            if (client != null) {
                client.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            }
            context.stop();
        }
    }
}
