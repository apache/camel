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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.spi.ScheduledPollConsumerScheduler;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScheduledPollConsumerTest extends ContextTestSupport {

    // the poll that throws an Error, after successful polls
    private static final int ERROR_ON_POLL = 3;

    private static boolean rollback;
    private static String event = "";

    @Test
    public void testExceptionOnPollAndCanStartAgain() {
        final Exception expectedException = new Exception("Hello, I should be thrown on shutdown only!");
        final Endpoint endpoint = getMockEndpoint("mock:foo");
        MockScheduledPollConsumer consumer = new MockScheduledPollConsumer(endpoint, expectedException);

        consumer.setPollStrategy(new PollingConsumerPollStrategy() {
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
            }

            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) {
                if (e == expectedException) {
                    rollback = true;
                }
                return false;
            }
        });

        consumer.start();
        // poll that throws an exception
        consumer.run();
        consumer.stop();

        assertTrue(rollback, "Should have rollback");

        // prepare for 2nd run but this time it should not thrown an exception
        // on poll
        rollback = false;
        consumer.setExceptionToThrowOnPoll(null);
        // start it again and we should be able to run
        consumer.start();
        consumer.run();
        // should be able to stop with no problem
        consumer.stop();

        assertFalse(rollback, "Should not have rollback");
    }

    @Test
    public void testRetryAtMostThreeTimes() {
        final AtomicInteger counter = new AtomicInteger();
        event = "";

        final Exception expectedException = new Exception("Hello, I should be thrown on shutdown only!");
        final Endpoint endpoint = getMockEndpoint("mock:foo");
        MockScheduledPollConsumer consumer = new MockScheduledPollConsumer(endpoint, expectedException);

        consumer.setPollStrategy(new PollingConsumerPollStrategy() {
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                event += "commit";
            }

            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) {
                event += "rollback";
                int cnt = counter.incrementAndGet();
                if (cnt <= 3) {
                    return true;
                }
                return false;
            }
        });

        consumer.setUseFixedDelay(true);
        consumer.setDelay(60000);
        consumer.start();
        // poll that throws an exception
        consumer.run();
        consumer.stop();

        // 3 retries + 1 last failed attempt when we give up
        assertEquals(4, counter.get());
        assertEquals("rollbackrollbackrollbackrollback", event);
    }

    @Test
    public void testErrorOnPollStopsPollingAndHealthCheckIsDown() {
        final LinkageError expectedError = new LinkageError("Simulated error on poll");
        final AtomicInteger rollbacks = new AtomicInteger();
        MockScheduledPollConsumer consumer = createConsumerWithError(expectedError, rollbacks);
        consumer.start();
        pollUntilErrorIsNext(consumer);

        // the Error ends the scheduled task, so the scheduler does not run it again
        LinkageError thrown = assertThrows(LinkageError.class, consumer::run);
        assertSame(expectedError, thrown);
        // an Error is not an exception the poll strategy can retry
        assertEquals(0, rollbacks.get());

        HealthCheck.Result result = consumer.getHealthCheck().call();
        assertEquals(HealthCheck.State.DOWN, result.getState());
        assertSame(expectedError, result.getError().orElse(null));

        consumer.stop();
    }

    @Test
    public void testErrorOnPollKeepsHealthCheckDownUntilRestart() {
        final LinkageError expectedError = new LinkageError("Simulated error on poll");
        MockScheduledPollConsumer consumer = createConsumerWithError(expectedError, new AtomicInteger());
        consumer.start();
        pollUntilErrorIsNext(consumer);
        assertThrows(LinkageError.class, consumer::run);
        assertEquals(HealthCheck.State.DOWN, consumer.getHealthCheck().call().getState());

        // with concurrentConsumers > 1 the other tasks keep polling: a successful poll must not report the
        // consumer as healthy again, as it now polls with one task less
        consumer.run();
        HealthCheck.Result result = consumer.getHealthCheck().call();
        assertEquals(HealthCheck.State.DOWN, result.getState());
        assertSame(expectedError, result.getError().orElse(null));

        // only a restart of the consumer clears it
        consumer.stop();
        consumer.start();
        consumer.run();
        assertEquals(HealthCheck.State.UP, consumer.getHealthCheck().call().getState());

        consumer.stop();
    }

    @Test
    public void testErrorFromExceptionHandlerStopsPollingAndHealthCheckIsDown() {
        // such as a bridged error handler whose dead letter route throws a StackOverflowError
        final Exception pollException = new Exception("Simulated exception on poll");
        final StackOverflowError expectedError = new StackOverflowError("Simulated error in the exception handler");
        MockScheduledPollConsumer consumer = createConsumerWithException(pollException, null);
        consumer.setExceptionHandler(new ExceptionHandler() {
            public void handleException(Throwable exception) {
                handleException(null, exception);
            }

            public void handleException(String message, Throwable exception) {
                handleException(message, null, exception);
            }

            public void handleException(String message, Exchange exchange, Throwable exception) {
                throw expectedError;
            }
        });
        consumer.start();

        assertErrorStopsPollingAndHealthCheckIsDown(consumer, expectedError, pollException);

        consumer.stop();
    }

    @Test
    public void testErrorFromRollbackStopsPollingAndHealthCheckIsDown() {
        final Exception pollException = new Exception("Simulated exception on poll");
        final LinkageError expectedError = new LinkageError("Simulated error on rollback");
        MockScheduledPollConsumer consumer = createConsumerWithException(pollException, expectedError);
        consumer.start();

        assertErrorStopsPollingAndHealthCheckIsDown(consumer, expectedError, pollException);

        consumer.stop();
    }

    private static void assertErrorStopsPollingAndHealthCheckIsDown(
            MockScheduledPollConsumer consumer, Error expectedError, Exception pollException) {
        // the Error ends the scheduled task, so the scheduler does not run it again
        Error thrown = assertThrows(Error.class, consumer::run);
        assertSame(expectedError, thrown);

        HealthCheck.Result result = consumer.getHealthCheck().call();
        assertEquals(HealthCheck.State.DOWN, result.getState());
        assertSame(expectedError, result.getError().orElse(null));
        // the exception that was being handled is kept with the Error
        assertArrayEquals(new Throwable[] { pollException }, thrown.getSuppressed());

        // a poll by another task (scheduler.concurrentConsumers > 1) does not report the consumer as healthy again
        consumer.setExceptionToThrowOnPoll(null);
        consumer.run();
        assertEquals(HealthCheck.State.DOWN, consumer.getHealthCheck().call().getState());
    }

    private MockScheduledPollConsumer createConsumerWithException(Exception exception, Error rollbackError) {
        MockScheduledPollConsumer consumer = new MockScheduledPollConsumer(getMockEndpoint("mock:foo"), exception);
        consumer.setRouteId("error");
        // the test runs the polls itself
        consumer.setStartScheduler(false);
        consumer.setPollStrategy(new PollingConsumerPollStrategy() {
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
            }

            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) {
                if (rollbackError != null) {
                    throw rollbackError;
                }
                return false;
            }
        });
        return consumer;
    }

    @Test
    public void testErrorOnPollWithOtherSchedulerIsTheLastError() {
        // such as the Spring or Quartz scheduler, which keep running the task after an Error
        final LinkageError expectedError = new LinkageError("Simulated error on poll");
        MockScheduledPollConsumer consumer = createConsumerWithError(expectedError, new AtomicInteger());
        consumer.setScheduler(new OtherScheduler());
        consumer.start();
        pollUntilErrorIsNext(consumer);

        // the Error is still thrown to the scheduler
        assertSame(expectedError, assertThrows(LinkageError.class, consumer::run));
        // but the task keeps polling, so the consumer is not kept DOWN until restart
        assertNull(consumer.getFatalError());
        HealthCheck.Result result = consumer.getHealthCheck().call();
        assertEquals(HealthCheck.State.DOWN, result.getState());
        assertSame(expectedError, result.getError().orElse(null));

        // the next successful poll reports the consumer as healthy again, as for an exception
        consumer.run();
        assertEquals(HealthCheck.State.UP, consumer.getHealthCheck().call().getState());

        consumer.stop();
    }

    private static final class OtherScheduler extends ServiceSupport implements ScheduledPollConsumerScheduler {

        private CamelContext camelContext;

        @Override
        public void onInit(Consumer consumer) {
        }

        @Override
        public void scheduleTask(Runnable task) {
            // the test runs the polls itself
        }

        @Override
        public void unscheduleTask() {
        }

        @Override
        public void startScheduler() {
        }

        @Override
        public boolean isSchedulerStarted() {
            return true;
        }

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }
    }

    /**
     * Runs the successful polls before the one that throws the Error, so the health check is UP because the consumer
     * polled (and not because of its initial state) before the Error.
     */
    private void pollUntilErrorIsNext(MockScheduledPollConsumer consumer) {
        for (int i = 1; i < ERROR_ON_POLL; i++) {
            consumer.run();
            assertEquals(HealthCheck.State.UP, consumer.getHealthCheck().call().getState());
        }
    }

    private MockScheduledPollConsumer createConsumerWithError(Error error, AtomicInteger rollbacks) {
        MockScheduledPollConsumer consumer = new MockScheduledPollConsumer(getMockEndpoint("mock:foo"), (Exception) null);
        consumer.setErrorToThrowOnPoll(error, ERROR_ON_POLL);
        consumer.setRouteId("error");
        // the test runs the polls itself
        consumer.setStartScheduler(false);
        consumer.setPollStrategy(new PollingConsumerPollStrategy() {
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
            }

            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) {
                rollbacks.incrementAndGet();
                return false;
            }
        });
        return consumer;
    }

    @Test
    public void testNoExceptionOnPoll() {
        final Endpoint endpoint = getMockEndpoint("mock:foo");
        MockScheduledPollConsumer consumer = new MockScheduledPollConsumer(endpoint, null);
        consumer.start();
        consumer.run();
        consumer.stop();
    }

}
