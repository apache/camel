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
package org.apache.camel.component.thrift;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stopping the client manager of the asynchronous producer ends its selector thread without completing the calls it
 * still has: the producer must fail their exchanges when it stops, else they never complete.
 */
class ThriftProducerAsyncInFlightStopTest extends CamelTestSupport {

    @Test
    void stoppingTheProducerFailsAnInFlightCall() throws Exception {
        CountDownLatch requestReceived = new CountDownLatch(1);
        try (ServerSocket server = new ServerSocket(0)) {
            // a server that reads the request and never answers
            Thread silent = new Thread(() -> {
                try (Socket socket = server.accept(); InputStream in = socket.getInputStream()) {
                    if (in.read() != -1) {
                        requestReceived.countDown();
                    }
                    while (in.read() != -1) {
                        // keep the connection open until the client closes it
                    }
                } catch (Exception e) {
                    // the test ends
                }
            });
            silent.setDaemon(true);
            silent.start();

            String uri = "thrift://localhost:" + server.getLocalPort()
                         + "/org.apache.camel.component.thrift.generated.Calculator?method=ping";
            ThriftProducer producer = (ThriftProducer) context.getEndpoint(uri).createProducer();
            producer.start();
            Exchange exchange = context.getEndpoint(uri).createExchange();
            CountDownLatch done = new CountDownLatch(1);
            ((AsyncProducer) producer).process(exchange, doneSync -> done.countDown());
            assertTrue(requestReceived.await(10, TimeUnit.SECONDS), "the call must be sent");
            assertEquals(1, producer.getPendingCallbacks());

            producer.stop();

            assertTrue(done.await(10, TimeUnit.SECONDS), "the exchange of the call must complete");
            assertInstanceOf(CamelExchangeException.class, exchange.getException());
            assertTrue(exchange.getException().getMessage().startsWith(
                    "The Thrift producer was stopped before the call of method ping completed"),
                    exchange.getException().getMessage());
            assertEquals(0, producer.getPendingCallbacks());
        }
    }

    @Test
    void aCompletedCallIsNotPending() throws Exception {
        // control: the callback of a call that completed (here with an error: nothing listens) is not kept
        int port;
        try (ServerSocket closed = new ServerSocket(0)) {
            port = closed.getLocalPort();
        }
        String uri = "thrift://localhost:" + port
                     + "/org.apache.camel.component.thrift.generated.Calculator?method=ping";
        ThriftProducer producer = (ThriftProducer) context.getEndpoint(uri).createProducer();
        producer.start();
        try {
            Exchange exchange = context.getEndpoint(uri).createExchange();
            CountDownLatch done = new CountDownLatch(1);
            producer.process(exchange, doneSync -> done.countDown());

            assertTrue(done.await(10, TimeUnit.SECONDS));
            assertTrue(exchange.isFailed());
            assertEquals(0, producer.getPendingCallbacks());
        } finally {
            producer.stop();
        }
    }
}
