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
package org.apache.camel.component.vertx.websocket;

import java.net.ConnectException;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.LockSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Route policies start and stop consumers without the CamelContext lock, so consumers sharing a host and port can
 * connect and disconnect at the same time. A consumer must not end up on a host that a disconnect removed from the
 * registry meanwhile, as then its stop would not close the server.
 */
class VertxWebsocketSharedHostStartStopRaceTest extends VertxWebSocketTestSupport {

    private final CountDownLatch connectReached = new CountDownLatch(1);
    private final CountDownLatch connectReleased = new CountDownLatch(1);
    private final Map<FutureTask<Void>, Thread> taskThreads = new ConcurrentHashMap<>();
    private volatile Consumer pauseConnectOf;

    @AfterEach
    void release() {
        connectReleased.countDown();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.addComponent("vertx-websocket", new PausingComponent());
        return camelContext;
    }

    @Test
    void startWhileTheLastConsumerStops() throws Exception {
        Consumer a = context.getRoute("a").getConsumer();
        Consumer b = context.getRoute("b").getConsumer();
        ServiceHelper.stopService(b);

        // b gets the host of a from the registry, and pauses before it connects
        pauseConnectOf = b;
        FutureTask<Void> startB = run(() -> ServiceHelper.startService(b));
        assertTrue(connectReached.await(10, TimeUnit.SECONDS));

        // a is the last consumer of the host
        FutureTask<Void> stopA = run(() -> ServiceHelper.stopService(a));
        awaitDoneOrWaitingOnALock(stopA);
        connectReleased.countDown();
        startB.get(10, TimeUnit.SECONDS);
        stopA.get(10, TimeUnit.SECONDS);

        ServiceHelper.stopService(b);

        assertThrows(ConnectException.class, () -> new Socket("localhost", port.getPort()).close(),
                "a server still listens after both consumers stopped");
        assertTrue(isRegistryEmpty());
    }

    @Test
    void startWhileAnotherConsumerFailsToStart() throws Exception {
        Consumer a = context.getRoute("a").getConsumer();
        ServiceHelper.stopService(a, context.getRoute("b").getConsumer());
        // the host refuses this consumer, as it has other SSLContextParameters, and its failed start stops it
        VertxWebsocketEndpoint tlsEndpoint = context.getEndpoint(
                "vertx-websocket:localhost:" + port.getPort() + "/test/tls", VertxWebsocketEndpoint.class);
        tlsEndpoint.getConfiguration().setSslContextParameters(new SSLContextParameters());
        Consumer tls = tlsEndpoint.createConsumer(exchange -> {
        });

        // a creates the host, and pauses before it connects
        pauseConnectOf = a;
        FutureTask<Void> startA = run(() -> ServiceHelper.startService(a));
        assertTrue(connectReached.await(10, TimeUnit.SECONDS));

        FutureTask<Void> startTls = run(() -> ServiceHelper.startService(tls));
        awaitDoneOrWaitingOnALock(startTls);
        connectReleased.countDown();
        startA.get(10, TimeUnit.SECONDS);
        Exception refused = assertThrows(Exception.class, () -> startTls.get(10, TimeUnit.SECONDS));
        assertTrue(refused.getMessage().contains("SSLContextParameters"), "unexpected failure: " + refused);

        ServiceHelper.stopService(a);

        assertThrows(ConnectException.class, () -> new Socket("localhost", port.getPort()).close(),
                "a server still listens after the consumer stopped");
        assertTrue(isRegistryEmpty());
    }

    private FutureTask<Void> run(Runnable runnable) {
        FutureTask<Void> task = new FutureTask<>(runnable, null);
        Thread thread = new Thread(task, "start-stop-race");
        thread.setDaemon(true);
        taskThreads.put(task, thread);
        thread.start();
        return task;
    }

    /**
     * Waits until the task is done or parked on a lock: with the fix, the lock of the host key that the paused connect
     * holds (a stop that waits for the server to close waits on a future, not on a lock)
     */
    private void awaitDoneOrWaitingOnALock(FutureTask<Void> task) {
        Thread thread = taskThreads.get(task);
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> task.isDone() || LockSupport.getBlocker(thread) instanceof AbstractQueuedSynchronizer);
    }

    private boolean isRegistryEmpty() {
        return context.getComponent("vertx-websocket", VertxWebsocketComponent.class).getVertxHostRegistry().isEmpty();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/test/a", port.getPort()).routeId("a").to("mock:a");
                fromF("vertx-websocket:localhost:%d/test/b", port.getPort()).routeId("b").to("mock:b");
            }
        };
    }

    /**
     * Pauses the chosen consumer after it got its host from the registry, before the host connects it
     */
    private class PausingComponent extends VertxWebsocketComponent {
        @Override
        protected VertxWebsocketHost createVertxWebsocketHost(
                VertxWebsocketHostConfiguration hostConfiguration, VertxWebsocketHostKey hostKey) {
            return new VertxWebsocketHost(getCamelContext(), hostConfiguration, hostKey) {
                @Override
                public void connect(VertxWebsocketConsumer consumer) {
                    if (consumer == pauseConnectOf) {
                        pauseConnectOf = null;
                        connectReached.countDown();
                        try {
                            connectReleased.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    super.connect(consumer);
                }
            };
        }
    }
}
