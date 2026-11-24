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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.timeout.ReadTimeoutException;
import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An exception on a pooled channel that no request uses yet (here the server sends a line longer than
 * decoderMaxLineLength to the channel that lazyChannelCreation=false opened at start) must not keep the next request on
 * that channel from completing: by its request timeout, or when the server closes the connection.
 */
class NettyProducerStrayExceptionTest extends CamelTestSupport {

    private final CountDownLatch sendGreeting = new CountDownLatch(1);
    private final CountDownLatch requestReceived = new CountDownLatch(1);
    private final CountDownLatch exceptionSeen = new CountDownLatch(1);
    private ServerSocket server;
    private Socket accepted;
    private AsyncProducer producer;

    @AfterEach
    void cleanup() throws Exception {
        ServiceHelper.stopService(producer);
        if (accepted != null) {
            accepted.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void testRequestTimeoutAfterExceptionOnUnusedChannel() throws Exception {
        startServer("x".repeat(200), false);
        startProducerAndAwaitException("&requestTimeout=1000");

        Exchange exchange = sendRequest();

        assertInstanceOf(ReadTimeoutException.class, exchange.getException());
    }

    @Test
    void testServerCloseAfterExceptionOnUnusedChannel() throws Exception {
        startServer("x".repeat(200), true);
        startProducerAndAwaitException("");

        Exchange exchange = sendRequest();

        assertInstanceOf(CamelExchangeException.class, exchange.getException());
        assertTrue(exchange.getException().getMessage().startsWith("No response received from remote server"),
                exchange.getException().getMessage());
    }

    @Test
    void testRequestTimeoutWithoutException() throws Exception {
        // the server sends nothing before the request, so there is no exception on the unused channel
        startServer(null, false);
        startProducer("&requestTimeout=1000");

        Exchange exchange = sendRequest();

        assertInstanceOf(ReadTimeoutException.class, exchange.getException());
    }

    private Exchange sendRequest() throws InterruptedException {
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getMessage().setBody("hello");
        CountDownLatch done = new CountDownLatch(1);
        producer.process(exchange, doneSync -> done.countDown());
        assertTrue(requestReceived.await(5, TimeUnit.SECONDS), "the server should get the request");
        assertTrue(done.await(10, TimeUnit.SECONDS), "the exchange should be completed");
        return exchange;
    }

    private Channel startProducer(String options) throws Exception {
        producer = context.getEndpoint("netty:tcp://localhost:" + server.getLocalPort()
                                       + "?textline=true&sync=true&lazyChannelCreation=false&decoderMaxLineLength=64"
                                       + options)
                .createAsyncProducer();
        ServiceHelper.startService(producer);
        // the channel the producer opened at start, unused in its pool (a listener of the connect future adds it to
        // the group on the event loop, which may run just after the start returned)
        NettyProducer nettyProducer = (NettyProducer) producer;
        await().atMost(5, TimeUnit.SECONDS).until(() -> !nettyProducer.getAllChannels().isEmpty());
        return nettyProducer.getAllChannels().iterator().next();
    }

    private void startProducerAndAwaitException(String options) throws Exception {
        Channel channel = startProducer(options);
        // see the exception before the producer's handler gets it, and count down once that handler is done with it
        channel.pipeline().addBefore("handler", "exceptionProbe", new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                ctx.fireExceptionCaught(cause);
                if (cause instanceof TooLongFrameException) {
                    exceptionSeen.countDown();
                }
            }
        });
        sendGreeting.countDown();
        assertTrue(exceptionSeen.await(5, TimeUnit.SECONDS), "the long greeting should fail in the decoder");
        assertTrue(channel.isActive(), "the channel stays open in the pool");
    }

    private void startServer(String greeting, boolean closeAfterRequest) throws Exception {
        server = new ServerSocket(0);
        Thread thread = new Thread(() -> {
            try {
                accepted = server.accept();
                if (greeting != null) {
                    sendGreeting.await(10, TimeUnit.SECONDS);
                    OutputStream out = accepted.getOutputStream();
                    out.write((greeting + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(accepted.getInputStream(), StandardCharsets.UTF_8));
                if (in.readLine() != null) {
                    requestReceived.countDown();
                }
                if (closeAfterRequest) {
                    accepted.close();
                }
                // otherwise there is no reply
            } catch (Exception e) {
                // the test fails on its own latches
            }
        }, "NettyProducerStrayExceptionTestServer");
        thread.setDaemon(true);
        thread.start();
    }
}
