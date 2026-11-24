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

import io.netty.channel.embedded.EmbeddedChannel;
import org.apache.camel.AsyncProducer;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.TimeoutMap;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A correlation manager bean extending TimeoutCorrelationManagerSupport can be used by several producers (the producers
 * of one endpoint, or of endpoints that refer to the same bean). A producer that stops must not stop it under the
 * others: it is stopped by the last producer that stops.
 */
class NettyTimeoutCorrelationManagerSharedTest extends CamelTestSupport {

    @BindToRegistry("slowManager")
    private final MyCorrelationManager slowManager = new MyCorrelationManager(60000);

    @BindToRegistry("fastManager")
    private final MyCorrelationManager fastManager = new MyCorrelationManager(300);

    private final List<Socket> accepted = new CopyOnWriteArrayList<>();
    private final List<AsyncProducer> producers = new CopyOnWriteArrayList<>();
    private final BlockingQueue<Request> received = new LinkedBlockingQueue<>();
    // the waits of the hooks that timed out
    private final List<String> missed = new CopyOnWriteArrayList<>();
    private ServerSocket server;

    private record Request(String line, Socket socket) {
    }

    private static final class Tracked {
        final NettyCamelState state;
        final AtomicInteger calls = new AtomicInteger();
        final CountDownLatch done = new CountDownLatch(1);

        Tracked(Exchange exchange) {
            state = new NettyCamelState(doneSync -> {
                calls.incrementAndGet();
                done.countDown();
            }, exchange);
        }
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
        for (AsyncProducer producer : producers) {
            ServiceHelper.stopService(producer);
        }
        for (Socket socket : accepted) {
            socket.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void testStoppingAndRestartingOneProducerKeepsTheRequestOfTheOther() throws Exception {
        Endpoint endpoint = context.getEndpoint(uri("slowManager"));
        AsyncProducer p1 = start(endpoint.createAsyncProducer());
        AsyncProducer p2 = start(endpoint.createAsyncProducer());

        Sent sent = send(p2, "abc-hello");
        Request request = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(request, "the server should get the request");

        // p1 stops (its route is stopped, for example) and starts again while the request of p2 waits for its reply
        ServiceHelper.stopService(p1);
        ServiceHelper.startService(p1);
        reply(request, "abc-world");

        assertTrue(sent.done.await(10, TimeUnit.SECONDS), "the request should get its reply");
        assertNull(sent.exchange.getException(), "the request should not be completed by the stop of the other producer");
        assertEquals("abc-world", sent.exchange.getMessage().getBody(String.class));
    }

    @Test
    void testRequestOfTheRunningProducerTimesOutAfterTheOtherStopped() throws Exception {
        Endpoint endpoint = context.getEndpoint(uri("fastManager"));
        AsyncProducer p1 = start(endpoint.createAsyncProducer());
        AsyncProducer p2 = start(endpoint.createAsyncProducer());

        ServiceHelper.stopService(p1);
        assertTrue(fastManager.isStarted(), "the manager should still run for p2");

        // the server never replies
        Sent sent = send(p2, "abc-hello");
        assertTrue(sent.done.await(10, TimeUnit.SECONDS), "the request should time out");
        assertInstanceOf(ExchangeTimedOutException.class, sent.exchange.getException());
    }

    @Test
    void testLastProducerThatStopsCompletesThePendingRequests() throws Exception {
        Endpoint endpoint = context.getEndpoint(uri("slowManager"));
        AsyncProducer p1 = start(endpoint.createAsyncProducer());
        AsyncProducer p2 = start(endpoint.createAsyncProducer());

        Sent first = send(p1, "abc-hello");
        Sent second = send(p2, "def-hello");
        assertNotNull(received.poll(5, TimeUnit.SECONDS), "the server should get the first request");
        assertNotNull(received.poll(5, TimeUnit.SECONDS), "the server should get the second request");

        ServiceHelper.stopService(p1);
        assertTrue(slowManager.isStarted(), "the manager should still run for p2");
        ServiceHelper.stopService(p2);

        // the server never replies, and the timeout of the manager is 60 seconds
        assertTrue(first.done.await(10, TimeUnit.SECONDS), "the stop of the last producer should complete the request");
        assertTrue(second.done.await(10, TimeUnit.SECONDS), "the stop of the last producer should complete the request");
        assertInstanceOf(ExchangeTimedOutException.class, first.exchange.getException());
        assertInstanceOf(ExchangeTimedOutException.class, second.exchange.getException());
        assertTrue(slowManager.isStopped(), "the last producer should stop the manager");
    }

    @Test
    void testProducerThatFailsToStartAgainDoesNotStopTheManager() throws Exception {
        try (ServerSocket other = new ServerSocket(0)) {
            // lazyChannelCreation=false: the producer connects when it starts, and fails to start without a server
            AsyncProducer p1 = start(context.getEndpoint("netty:tcp://localhost:" + other.getLocalPort()
                                                         + "?textline=true&sync=true&lazyChannelCreation=false"
                                                         + "&correlationManager=#slowManager")
                    .createAsyncProducer());
            AsyncProducer p2 = start(context.getEndpoint(uri("slowManager")).createAsyncProducer());

            ServiceHelper.stopService(p1);
            other.close();
            assertThrows(Exception.class, () -> ServiceHelper.startService(p1));
            assertTrue(slowManager.isStarted(), "the producer that failed to start should not stop the manager of p2");

            Sent sent = send(p2, "abc-hello");
            Request request = received.poll(5, TimeUnit.SECONDS);
            assertNotNull(request, "the server should get the request");
            reply(request, "abc-world");
            assertTrue(sent.done.await(10, TimeUnit.SECONDS), "the request should get its reply");
            assertNull(sent.exchange.getException());
            assertEquals("abc-world", sent.exchange.getMessage().getBody(String.class));
        }
    }

    @Test
    void testRequestFailsWhenTheManagerIsStopped() throws Exception {
        AsyncProducer producer = start(context.getEndpoint(uri("slowManager")).createAsyncProducer());

        // the bean is stopped by something else than the producer while the producer runs
        ServiceHelper.stopService(slowManager);

        // the server never replies: the request must fail rather than wait for a timeout that never fires
        Sent sent = send(producer, "abc-hello");
        assertTrue(sent.done.await(10, TimeUnit.SECONDS), "the request should fail");
        assertInstanceOf(IllegalStateException.class, sent.exchange.getException());
    }

    @Test
    void testStateFailsWhenTheManagerStopsWhilePutting() {
        // the manager stops after putState checked that it runs, and before the state is in its map: the drain of the
        // stop does not see the state, so putState must fail it rather than leave it in the map of a stopped manager
        HookCorrelationManager manager = new HookCorrelationManager(context);
        manager.retain();
        manager.idHook = () -> ServiceHelper.stopService(manager);

        Tracked request = track("abc-hello");
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            assertThrows(IllegalStateException.class, () -> manager.putState(channel, request.state));
            assertTrue(manager.isStopped());
            // putState took the state back and owns its completion: the producer completes the exchange with the
            // exception, and nothing else touches it
            assertTrue(request.state.isDone());
            assertEquals(0, request.calls.get());
        } finally {
            channel.close();
        }
    }

    @Test
    void testStatePutAfterTheDrainOfTheStopFails() throws Exception {
        // the stop drains the map, then stops the purge task and clears the map. A state put after the drain is in no
        // map once the stop ended, so putState, which checks again after the put, must fail it
        HookCorrelationManager manager = new HookCorrelationManager(context);
        manager.retain();
        EmbeddedChannel channel = new EmbeddedChannel();
        // a request that waits for its reply, so the drain has an entry to evict, where the stop is held
        Tracked pending = track("zzz-pending");
        manager.putState(channel, pending.state);

        CountDownLatch drained = new CountDownLatch(1);
        CountDownLatch put = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        manager.getTimeoutMap().addListener((type, key, value) -> {
            if (type == TimeoutMap.Listener.Type.Evict && "zzz".equals(key)) {
                // the drain took the entries of the map: the state is put before the stop goes on
                drained.countDown();
                await(put, "put");
            } else if (type == TimeoutMap.Listener.Type.Put && "abc".equals(key)) {
                // the state is in the map: putState checks again once the stop ended
                put.countDown();
                await(stopped, "stopped");
            }
        });
        Thread stopper = new Thread(() -> {
            // the last producer that uses the manager stops
            manager.release();
            stopped.countDown();
        }, "NettyTimeoutCorrelationManagerSharedTestStopper");
        manager.idHook = () -> {
            stopper.start();
            await(drained, "drained");
        };

        Tracked request = track("abc-hello");
        try {
            assertThrows(IllegalStateException.class, () -> manager.putState(channel, request.state));
            stopper.join(10000);
            assertTrue(missed.isEmpty(), "the hooks timed out: " + missed);
            assertTrue(manager.isStopped());
            assertTrue(request.state.isDone());
            assertEquals(0, request.calls.get(), "the stop must not complete the state that putState fails");
            assertTrue(pending.done.await(10, TimeUnit.SECONDS), "the drain should complete the pending request");
            assertInstanceOf(ExchangeTimedOutException.class, pending.state.getExchange().getException());
        } finally {
            channel.close();
        }
    }

    @Test
    void testStateFailsWhenTheManagerStopsAndStartsAgainWhilePutting() {
        // the last producer stops and another one starts after putState took the map: the state goes into the map of
        // the previous start, which is stopped, and the reply would be looked up in the new map
        HookCorrelationManager manager = new HookCorrelationManager(context);
        manager.retain();
        manager.idHook = () -> {
            manager.release();
            manager.retain();
        };

        Tracked request = track("abc-hello");
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            assertThrows(IllegalStateException.class, () -> manager.putState(channel, request.state));
            assertTrue(manager.isStarted());
            assertTrue(request.state.isDone());
            assertEquals(0, request.calls.get());
            assertNull(manager.getState(null, channel, (Object) "abc-world"), "the state should be in no map");
        } finally {
            channel.close();
            manager.release();
        }
    }

    @Test
    void testStateFailsWhenPutWhileTheManagerStartsAgain() throws Exception {
        // while the manager starts again it still has the map of its previous start, which is stopped, until the
        // start replaced it: a state put in that map would never be completed
        HookCorrelationManager manager = new HookCorrelationManager(context);
        manager.retain();
        manager.release();

        CountDownLatch starting = new CountDownLatch(1);
        CountDownLatch putDone = new CountDownLatch(1);
        manager.startHook = () -> {
            starting.countDown();
            await(putDone, "putDone");
        };
        Thread starter = new Thread(manager::retain, "NettyTimeoutCorrelationManagerSharedTestStarter");
        starter.start();
        assertTrue(starting.await(10, TimeUnit.SECONDS), "the manager should start");

        Tracked request = track("abc-hello");
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            assertThrows(IllegalStateException.class, () -> manager.putState(channel, request.state));
        } finally {
            putDone.countDown();
            starter.join(10000);
            channel.close();
        }
        assertTrue(missed.isEmpty(), "the hooks timed out: " + missed);
        assertTrue(manager.isStarted());
        assertNull(manager.getState(null, channel, (Object) "abc-world"), "the state should be in no map");
        manager.release();
    }

    @Test
    void testStateCompletedByTheDrainWhilePuttingIsCompletedOnce() throws Exception {
        // the manager stops right after the state is put: the drain of the stop completes it with a timeout, so
        // putState must not fail it as well, or the producer would complete the exchange a second time
        HookCorrelationManager manager = new HookCorrelationManager(context);
        manager.retain();
        Tracked request = track("abc-hello");
        manager.getTimeoutMap().addListener((type, key, value) -> {
            if (type == TimeoutMap.Listener.Type.Put && "abc".equals(key)) {
                manager.release();
                await(request.done, "done");
            }
        });

        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            manager.putState(channel, request.state);
        } finally {
            channel.close();
        }
        assertTrue(missed.isEmpty(), "the hooks timed out: " + missed);
        assertTrue(manager.isStopped());
        assertEquals(1, request.calls.get());
        assertInstanceOf(ExchangeTimedOutException.class, request.state.getExchange().getException());
    }

    private String uri(String manager) throws Exception {
        if (server == null) {
            startServer();
        }
        return "netty:tcp://localhost:" + server.getLocalPort() + "?textline=true&sync=true&correlationManager=#" + manager;
    }

    private AsyncProducer start(AsyncProducer producer) {
        producers.add(producer);
        ServiceHelper.startService(producer);
        return producer;
    }

    private Tracked track(String body) {
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getMessage().setBody(body);
        return new Tracked(exchange);
    }

    private void await(CountDownLatch latch, String name) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                missed.add(name);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            missed.add(name);
        }
    }

    private Sent send(AsyncProducer producer, String body) {
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
                    }, "NettyTimeoutCorrelationManagerSharedTestReader");
                    reader.setDaemon(true);
                    reader.start();
                }
            } catch (Exception e) {
                // the server socket was closed
            }
        }, "NettyTimeoutCorrelationManagerSharedTestServer");
        thread.setDaemon(true);
        thread.start();
    }

    private static class MyCorrelationManager extends TimeoutCorrelationManagerSupport {
        MyCorrelationManager(long timeout) {
            setTimeout(timeout);
            setTimeoutChecker(100);
        }

        @Override
        public String getRequestCorrelationId(Object request) {
            return request.toString().substring(0, 3);
        }

        @Override
        public String getResponseCorrelationId(Object response) {
            return response.toString().substring(0, 3);
        }
    }

    /**
     * Runs a hook once while it reads the correlation id of a request, which is after putState checked that it runs and
     * before it puts the state in its map, and a hook once when it starts.
     */
    private static final class HookCorrelationManager extends MyCorrelationManager {
        volatile Runnable idHook;
        volatile Runnable startHook;

        HookCorrelationManager(CamelContext context) {
            super(60000);
            setCamelContext(context);
        }

        @Override
        public String getRequestCorrelationId(Object request) {
            Runnable hook = idHook;
            idHook = null;
            if (hook != null) {
                hook.run();
            }
            return super.getRequestCorrelationId(request);
        }

        @Override
        protected void doStart() throws Exception {
            Runnable hook = startHook;
            startHook = null;
            if (hook != null) {
                hook.run();
            }
            super.doStart();
        }
    }
}
