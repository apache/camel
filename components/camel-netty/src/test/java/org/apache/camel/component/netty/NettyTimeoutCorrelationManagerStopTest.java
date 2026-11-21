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
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncProducer;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A request still waiting for its reply when the producer stops must be completed, also with a correlation manager that
 * extends TimeoutCorrelationManagerSupport (whose timeout map would otherwise be cleared without completing it).
 */
class NettyTimeoutCorrelationManagerStopTest extends CamelTestSupport {

    @BindToRegistry("myManager")
    private final MyCorrelationManager myManager = new MyCorrelationManager(60000);

    @BindToRegistry("rejectingManager")
    private final MyCorrelationManager rejectingManager = new MyCorrelationManager(300);

    private final List<Socket> accepted = new CopyOnWriteArrayList<>();
    private ServerSocket server;
    private AsyncProducer producer;

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
    void testStopCompletesPendingRequestOfTimeoutCorrelationManager() throws Exception {
        List<Exchange> exchanges = sendAndStop("&correlationManager=#myManager", "abc-hello");

        assertInstanceOf(ExchangeTimedOutException.class, exchanges.get(0).getException());
    }

    @Test
    void testStopCompletesPendingRequestsOnSharedChannel() throws Exception {
        // producerPoolEnabled=false: both requests wait on the one channel, and the manager correlates the replies
        List<Exchange> exchanges
                = sendAndStop("&correlationManager=#myManager&producerPoolEnabled=false", "abc-hello", "def-hello");

        assertInstanceOf(ExchangeTimedOutException.class, exchanges.get(0).getException());
        assertInstanceOf(ExchangeTimedOutException.class, exchanges.get(1).getException());
    }

    @Test
    void testStopCompletesPendingRequestOfDefaultCorrelationManager() throws Exception {
        List<Exchange> exchanges = sendAndStop("", "abc-hello");

        assertInstanceOf(CamelExchangeException.class, exchanges.get(0).getException());
    }

    @Test
    void testTimeoutRejectedByTheWorkerPoolCompletesTheRequest() throws Exception {
        // a worker pool that no longer takes tasks (as an owned pool after the producer stopped): the timeout is
        // completed in the thread that evicted it
        ExecutorService workerPool = Executors.newSingleThreadExecutor();
        workerPool.shutdown();
        rejectingManager.setWorkerPool(workerPool);
        CountDownLatch requestsReceived = new CountDownLatch(1);
        startSilentServer(requestsReceived);
        producer = context.getEndpoint("netty:tcp://localhost:" + server.getLocalPort()
                                       + "?textline=true&sync=true&correlationManager=#rejectingManager")
                .createAsyncProducer();
        ServiceHelper.startService(producer);

        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getMessage().setBody("abc-hello");
        CountDownLatch done = new CountDownLatch(1);
        producer.process(exchange, doneSync -> done.countDown());

        assertTrue(done.await(10, TimeUnit.SECONDS), "the request should be completed by its timeout");
        assertInstanceOf(ExchangeTimedOutException.class, exchange.getException());
    }

    private List<Exchange> sendAndStop(String options, String... bodies) throws Exception {
        CountDownLatch requestsReceived = new CountDownLatch(bodies.length);
        startSilentServer(requestsReceived);
        producer = context.getEndpoint("netty:tcp://localhost:" + server.getLocalPort() + "?textline=true&sync=true"
                                       + options)
                .createAsyncProducer();
        ServiceHelper.startService(producer);

        List<Exchange> exchanges = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(bodies.length);
        for (String body : bodies) {
            Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
            exchange.getMessage().setBody(body);
            exchanges.add(exchange);
            producer.process(exchange, doneSync -> done.countDown());
        }
        assertTrue(requestsReceived.await(5, TimeUnit.SECONDS), "the server should get the requests");

        // the server never replies, and the timeout of the correlation manager is 60 seconds
        ServiceHelper.stopService(producer);

        assertTrue(done.await(10, TimeUnit.SECONDS), "the pending exchanges should be completed by the stop");
        assertEquals(bodies.length, exchanges.size());
        return exchanges;
    }

    private void startSilentServer(CountDownLatch requestsReceived) throws Exception {
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
                            while (in.readLine() != null) {
                                requestsReceived.countDown();
                            }
                        } catch (Exception e) {
                            // the socket was closed
                        }
                    }, "NettyTimeoutCorrelationManagerStopTestReader");
                    reader.setDaemon(true);
                    reader.start();
                }
            } catch (Exception e) {
                // the server socket was closed
            }
        }, "NettyTimeoutCorrelationManagerStopTestServer");
        thread.setDaemon(true);
        thread.start();
    }

    private static final class MyCorrelationManager extends TimeoutCorrelationManagerSupport {
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
}
