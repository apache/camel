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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import org.apache.camel.AsyncProducer;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With a correlation manager extending {@link TimeoutCorrelationManagerSupport}, a failed write and the timeout of the
 * request must complete the exchange only once, whichever comes first.
 */
public class NettyTimeoutCorrelationManagerWriteFailureTest extends BaseNettyTest {

    // counts down when the worker pool of the correlation manager has processed the timeout
    private final CountDownLatch timeoutProcessed = new CountDownLatch(1);
    private final ThreadPoolExecutor workerPool = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()) {
        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            timeoutProcessed.countDown();
        }
    };

    @BindToRegistry("myManager")
    private final MyCorrelationManager myManager = new MyCorrelationManager();

    @BindToRegistry("failingWrite")
    private final FailingWriteHandler failingWrite = new FailingWriteHandler();

    private final List<Throwable> completions = new CopyOnWriteArrayList<>();
    private AsyncProducer producer;

    @AfterEach
    public void stopProducer() {
        ServiceHelper.stopService(producer);
        workerPool.shutdownNow();
    }

    @Test
    public void testWriteFailsBeforeTimeout() throws Exception {
        failingWrite.failImmediately = true;

        Exchange exchange = sendRequest();

        // the correlation timeout fires later for the same request
        assertTrue(timeoutProcessed.await(5, TimeUnit.SECONDS), "the timeout should have been processed");
        assertEquals(1, completions.size(), "the exchange should be completed once: " + completions);
        assertInstanceOf(IOException.class, completions.get(0));
        // and the exchange is not changed after it completed
        assertInstanceOf(IOException.class, exchange.getException());
    }

    @Test
    public void testWriteFailsAfterTimeout() throws Exception {
        failingWrite.failImmediately = false;

        Exchange exchange = sendRequest();
        assertTrue(timeoutProcessed.await(5, TimeUnit.SECONDS), "the timeout should have been processed");
        assertInstanceOf(ExchangeTimedOutException.class, completions.get(0));

        // now the write that is still pending fails, for example as the connection is reset
        failingWrite.failPendingWrite();

        assertEquals(1, completions.size(), "the exchange should be completed once: " + completions);
        assertInstanceOf(ExchangeTimedOutException.class, exchange.getException());
    }

    private Exchange sendRequest() throws Exception {
        producer = context.getEndpoint(
                "netty:tcp://localhost:{{port}}?sync=true&producerPoolEnabled=false&encoders=#failingWrite"
                                       + "&correlationManager=#myManager")
                .createAsyncProducer();
        ServiceHelper.startService(producer);

        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getMessage().setBody("abc-hello");
        CountDownLatch done = new CountDownLatch(1);
        producer.process(exchange, doneSync -> {
            completions.add(exchange.getException());
            done.countDown();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS), "the exchange should be completed");
        return exchange;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty:tcp://localhost:{{port}}?textline=true&sync=true").transform(body().prepend("Bye "));
            }
        };
    }

    private final class MyCorrelationManager extends TimeoutCorrelationManagerSupport {

        MyCorrelationManager() {
            setTimeout(200);
            setTimeoutChecker(50);
            setWorkerPool(workerPool);
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
     * Fails the write at once, or keeps it pending until {@link #failPendingWrite()} is called.
     */
    @ChannelHandler.Sharable
    public static final class FailingWriteHandler extends ChannelOutboundHandlerAdapter {

        private final CountDownLatch written = new CountDownLatch(1);
        private volatile boolean failImmediately;
        private volatile ChannelPromise pending;

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            ReferenceCountUtil.release(msg);
            if (failImmediately) {
                promise.setFailure(new IOException("Simulated write failure"));
            } else {
                pending = promise;
                written.countDown();
            }
        }

        void failPendingWrite() throws InterruptedException {
            assertTrue(written.await(5, TimeUnit.SECONDS), "the request should have been written");
            // listeners are notified in the order they were added, so when this one runs the write listener of the
            // producer has run as well
            CountDownLatch notified = new CountDownLatch(1);
            pending.addListener(f -> notified.countDown());
            pending.setFailure(new IOException("Simulated connection reset"));
            assertTrue(notified.await(5, TimeUnit.SECONDS), "the write listeners should have been notified");
        }
    }
}
