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
package org.apache.camel.component.netty;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import org.apache.camel.AsyncProducer;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With a TimeoutCorrelationManagerSupport, a request whose correlation id is used by another request that still waits
 * for its reply fails, rather than replace the state of the other request (which would then never be completed).
 */
class NettyTimeoutCorrelationManagerDuplicateIdTest extends CamelTestSupport {

    @BindToRegistry("myManager")
    private final CountingCorrelationManager myManager = new CountingCorrelationManager();

    private final List<Socket> accepted = new CopyOnWriteArrayList<>();
    private final BlockingQueue<Request> received = new LinkedBlockingQueue<>();
    private ServerSocket server;
    private AsyncProducer producer;

    private record Request(String line, Socket socket) {
    }

    private static final class Sent {
        final Exchange exchange;
        final CountDownLatch done = new CountDownLatch(1);

        Sent(Exchange exchange) {
            this.exchange = exchange;
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        ServiceHelper.stopService(producer);
        for (Socket socket : accepted) {
            socket.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void testSecondRequestWithTheIdOfAWaitingRequestFails() throws Exception {
        // producerPoolEnabled=false: both requests use the one channel, and the manager correlates the replies
        startProducer("&sync=true&producerPoolEnabled=false");

        Sent first = send("abc-first");
        Request request = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(request, "the server should get the first request");

        Sent second = send("abc-second");
        assertTrue(second.done.await(10, TimeUnit.SECONDS), "the second request should fail");
        assertInstanceOf(IllegalStateException.class, second.exchange.getException());
        assertEquals(1, first.done.getCount(), "the first request should still wait for its reply");

        reply(request, "abc-reply");
        assertTrue(first.done.await(10, TimeUnit.SECONDS), "the first request should get its reply");
        assertNull(first.exchange.getException());
        assertEquals("abc-reply", first.exchange.getMessage().getBody(String.class));
        assertEquals(2, myManager.puts.get());
    }

    @Test
    void testRequestsWithoutReplyAreNotPut() throws Exception {
        startProducer("&sync=false");

        Sent first = send("abc-first");
        Sent second = send("abc-second");

        assertTrue(first.done.await(10, TimeUnit.SECONDS), "the first request should be sent");
        assertTrue(second.done.await(10, TimeUnit.SECONDS), "the second request should be sent");
        assertNull(first.exchange.getException());
        assertNull(second.exchange.getException());
        assertEquals(0, myManager.puts.get(), "no reply is waited for, so no state should be put in the manager");
    }

    @Test
    void testRequestWithoutReplyIsSentWithoutCorrelationId() throws Exception {
        // with sync=false the correlation id is not read, so a request without one is sent (it was refused before)
        startProducer("&sync=false");

        Sent sent = send("ab");
        assertTrue(sent.done.await(10, TimeUnit.SECONDS), "the request should be sent");
        assertNull(sent.exchange.getException());
        Request request = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(request, "the server should get the request");
        assertEquals("ab", request.line());
    }

    @Test
    void testPutRefusesTheIdOfAStateThatIsNotDone() {
        CountingCorrelationManager manager = startManager();
        Channel channel = new EmbeddedChannel();
        try {
            NettyCamelState waiting = state("abc-first");
            manager.putState(channel, waiting);

            assertThrows(IllegalStateException.class, () -> manager.putState(channel, state("abc-second")));
            // the reply still finds the state of the request that waits for it
            assertSame(waiting, manager.getState(null, channel, (Object) "abc-reply"));
        } finally {
            channel.close();
            ServiceHelper.stopService(manager);
        }
    }

    @Test
    void testPutReplacesTheIdOfAStateThatIsDone() {
        // a state stays in the map after its write failed (done), until it expires: its id can be used again
        CountingCorrelationManager manager = startManager();
        Channel channel = new EmbeddedChannel();
        try {
            NettyCamelState failed = state("abc-first");
            manager.putState(channel, failed);
            assertTrue(failed.markDone());

            NettyCamelState next = state("abc-second");
            manager.putState(channel, next);
            assertSame(next, manager.getState(null, channel, (Object) "abc-reply"));
        } finally {
            channel.close();
            ServiceHelper.stopService(manager);
        }
    }

    private CountingCorrelationManager startManager() {
        CountingCorrelationManager manager = new CountingCorrelationManager();
        manager.setCamelContext(context);
        ServiceHelper.startService(manager);
        return manager;
    }

    private NettyCamelState state(String body) {
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getMessage().setBody(body);
        return new NettyCamelState(doneSync -> {
        }, exchange);
    }

    private void startProducer(String options) throws Exception {
        startServer();
        producer = context.getEndpoint("netty:tcp://localhost:" + server.getLocalPort()
                                       + "?textline=true&correlationManager=#myManager" + options)
                .createAsyncProducer();
        ServiceHelper.startService(producer);
    }

    private Sent send(String body) {
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getMessage().setBody(body);
        Sent sent = new Sent(exchange);
        producer.process(exchange, doneSync -> sent.done.countDown());
        return sent;
    }

    private static void reply(Request request, String line) throws Exception {
        OutputStream out = request.socket().getOutputStream();
        out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void startServer() throws Exception {
        server = new ServerSocket(0);
        Thread thread = new Thread(() -> {
            try {
                while (!server.isClosed()) {
                    Socket socket = server.accept();
                    accepted.add(socket);
                    Thread reader = new Thread(() -> {
                        try {
                            BufferedReader in = new BufferedReader(
                                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                            String line;
                            while ((line = in.readLine()) != null) {
                                received.add(new Request(line, socket));
                            }
                        } catch (Exception e) {
                            // the socket was closed
                        }
                    }, "NettyTimeoutCorrelationManagerDuplicateIdTestReader");
                    reader.setDaemon(true);
                    reader.start();
                }
            } catch (Exception e) {
                // the server socket was closed
            }
        }, "NettyTimeoutCorrelationManagerDuplicateIdTestServer");
        thread.setDaemon(true);
        thread.start();
    }

    private static final class CountingCorrelationManager extends TimeoutCorrelationManagerSupport {
        final AtomicInteger puts = new AtomicInteger();

        CountingCorrelationManager() {
            setTimeout(60000);
            setTimeoutChecker(100);
        }

        @Override
        public void putState(Channel channel, NettyCamelState state) {
            puts.incrementAndGet();
            super.putState(channel, state);
        }

        @Override
        public String getRequestCorrelationId(Object request) {
            // a body shorter than 3 characters has no correlation id
            String body = request.toString();
            return body.length() < 3 ? null : body.substring(0, 3);
        }

        @Override
        public String getResponseCorrelationId(Object response) {
            return response.toString().substring(0, 3);
        }
    }
}
