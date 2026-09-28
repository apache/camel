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
package org.apache.camel.impl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.support.EventDrivenPollingConsumer;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A receive() whose thread is interrupted while it waits for a message must return, and keep the interrupt status.
 */
class EventDrivenPollingConsumerInterruptTest extends ContextTestSupport {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EventDrivenPollingConsumer pollingConsumer;

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        // a receive() that is still waiting ends once the polling consumer is stopped
        ServiceHelper.stopAndShutdownService(pollingConsumer);
        executor.shutdownNow();
        super.tearDown();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testInterruptedReceiveReturns() throws Exception {
        context.start();
        pollingConsumer = assertIsInstanceOf(EventDrivenPollingConsumer.class,
                context.getEndpoint("direct:idle").createPollingConsumer());
        AtomicInteger interruptedExceptions = new AtomicInteger();
        pollingConsumer.setInterruptedExceptionHandler(new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                handleException(null, null, exception);
            }

            @Override
            public void handleException(String message, Throwable exception) {
                handleException(message, null, exception);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                if (exception instanceof InterruptedException) {
                    interruptedExceptions.incrementAndGet();
                }
            }
        });
        pollingConsumer.start();

        AtomicReference<Thread> receiver = new AtomicReference<>();
        AtomicBoolean interruptedAfterReceive = new AtomicBoolean();
        Future<Exchange> future = executor.submit(() -> {
            receiver.set(Thread.currentThread());
            Exchange answer = pollingConsumer.receive();
            interruptedAfterReceive.set(Thread.currentThread().isInterrupted());
            return answer;
        });
        await().atMost(10, TimeUnit.SECONDS).until(() -> isWaitingInReceive(receiver.get()));

        receiver.get().interrupt();

        // receive() returns instead of waiting again (which fails at once as the thread is still interrupted)
        await().atMost(5, TimeUnit.SECONDS).until(future::isDone);
        assertNull(future.get());
        assertTrue(interruptedAfterReceive.get(), "The interrupt status of the thread should be kept");
        assertEquals(1, interruptedExceptions.get());
    }

    private static boolean isWaitingInReceive(Thread thread) {
        if (thread == null
                || thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.TIMED_WAITING) {
            return false;
        }
        for (StackTraceElement element : thread.getStackTrace()) {
            if (EventDrivenPollingConsumer.class.getName().equals(element.getClassName())
                    && "receive".equals(element.getMethodName())) {
                return true;
            }
        }
        return false;
    }
}
