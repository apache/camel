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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.spi.HttpResponseAware;
import org.apache.camel.support.DefaultScheduledPollConsumerScheduler;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScheduledPollConsumerEdgeCasesTest extends ContextTestSupport {

    private static class MyConsumer extends MockScheduledPollConsumer {

        private CountDownLatch pollStarted;
        private CountDownLatch pollContinue;

        MyConsumer(Endpoint endpoint, Exception exceptionToThrowOnPoll) {
            super(endpoint, exceptionToThrowOnPoll);
        }

        @Override
        protected int poll() throws Exception {
            if (pollStarted != null) {
                pollStarted.countDown();
                pollContinue.await(10, TimeUnit.SECONDS);
            }
            return super.poll();
        }

        Throwable lastError() {
            return getLastError();
        }

        Map<String, Object> lastErrorDetails() {
            return getLastErrorDetails();
        }
    }

    private static class MyHttpException extends Exception implements HttpResponseAware {
        MyHttpException() {
            super("Service unavailable");
        }

        @Override
        public int getHttpResponseCode() {
            return 503;
        }

        @Override
        public void setHttpResponseCode(int code) {
            // noop
        }

        @Override
        public String getHttpResponseStatus() {
            return "Service Unavailable";
        }

        @Override
        public void setHttpResponseStatus(String status) {
            // noop
        }
    }

    @Test
    public void testChangedDelayUsedAfterRestart() {
        MyConsumer consumer = new MyConsumer(getMockEndpoint("mock:foo"), null);
        consumer.setStartScheduler(false);
        consumer.setDelay(20);
        consumer.start();
        consumer.stop();

        consumer.setDelay(2000);
        consumer.setInitialDelay(3000);
        consumer.start();
        try {
            DefaultScheduledPollConsumerScheduler scheduler = (DefaultScheduledPollConsumerScheduler) consumer.getScheduler();
            assertEquals(2000, scheduler.getDelay());
            assertEquals(3000, scheduler.getInitialDelay());
        } finally {
            consumer.stop();
        }
    }

    @Test
    public void testConcurrentUnscheduleTask() throws Exception {
        MyConsumer consumer = new MyConsumer(getMockEndpoint("mock:foo"), null);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 30; i++) {
            DefaultScheduledPollConsumerScheduler scheduler = new DefaultScheduledPollConsumerScheduler();
            scheduler.setCamelContext(context);
            scheduler.setConcurrentConsumers(8);
            scheduler.setPoolSize(8);
            scheduler.setInitialDelay(0);
            scheduler.setDelay(1);
            scheduler.onInit(consumer);
            CountDownLatch latch = new CountDownLatch(8);
            CyclicBarrier barrier = new CyclicBarrier(8);
            // each concurrent task unschedules the task at the same time (such as when the repeat count is reached)
            scheduler.scheduleTask(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    scheduler.unscheduleTask();
                } catch (Throwable e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            });
            scheduler.start();
            scheduler.startScheduler();
            assertTrue(latch.await(10, TimeUnit.SECONDS));
            scheduler.stop();
        }

        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    public void testPollRunningWhenStoppedDoesNotUpdateState() throws Exception {
        MyConsumer consumer = new MyConsumer(getMockEndpoint("mock:foo"), null);
        consumer.setStartScheduler(false);
        consumer.start();

        consumer.pollStarted = new CountDownLatch(1);
        consumer.pollContinue = new CountDownLatch(1);
        Thread thread = new Thread(consumer::run);
        thread.start();
        assertTrue(consumer.pollStarted.await(10, TimeUnit.SECONDS));

        // stop while polling, and then let the poll complete
        consumer.stop();
        consumer.pollContinue.countDown();
        thread.join(10000);

        assertFalse(consumer.isFirstPollDone());
        assertEquals(0, consumer.getSuccessCounter());
    }

    @Test
    public void testErrorThresholdWithoutBackoffMultiplier() {
        MyConsumer consumer = new MyConsumer(getMockEndpoint("mock:foo"), new IllegalStateException("Forced"));
        consumer.setBackoffErrorThreshold(2);
        consumer.start();
        try {
            for (int i = 0; i < 5; i++) {
                consumer.run();
            }
            assertEquals(5, consumer.getErrorCounter());
        } finally {
            consumer.stop();
        }
    }

    @Test
    public void testErrorCounterKeptWhenBackoffFinished() {
        MyConsumer consumer = new MyConsumer(getMockEndpoint("mock:foo"), new IllegalStateException("Forced"));
        consumer.setBackoffMultiplier(2);
        consumer.setBackoffErrorThreshold(1);
        consumer.start();
        try {
            // error
            consumer.run();
            assertEquals(1, consumer.getErrorCounter());
            // backoff (skip)
            consumer.run();
            assertEquals(1, consumer.getErrorCounter());
            // backoff finished, poll again which fails again
            consumer.run();
            assertEquals(2, consumer.getErrorCounter());
            // and backoff again (skip)
            consumer.run();
            assertEquals(2, consumer.getErrorCounter());
        } finally {
            consumer.stop();
        }
    }

    @Test
    public void testLastErrorDetails() {
        MyConsumer consumer = new MyConsumer(getMockEndpoint("mock:foo"), new MyHttpException());
        consumer.start();
        consumer.run();
        assertEquals(503, consumer.lastErrorDetails().get(HealthCheck.HTTP_RESPONSE_CODE));

        // another error without http details
        consumer.setExceptionToThrowOnPoll(new IllegalStateException("Forced"));
        consumer.run();
        assertTrue(consumer.lastError() instanceof IllegalStateException);
        assertNull(consumer.lastErrorDetails());

        // the error is cleared when stopped
        consumer.stop();
        assertNull(consumer.lastError());
        assertNull(consumer.lastErrorDetails());
    }
}
