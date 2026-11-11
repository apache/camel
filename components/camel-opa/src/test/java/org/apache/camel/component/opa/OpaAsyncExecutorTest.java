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
package org.apache.camel.component.opa;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpaAsyncExecutorTest {
    @Test
    void boundedSaturationRejectsWithoutRunningOnTheCaller() throws Exception {
        try (var context = new DefaultCamelContext(); var executor = new OpaAsyncExecutor(context, this, 1, 1, 5000)) {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch callbacks = new CountDownLatch(2);
            AtomicInteger count = new AtomicInteger();
            Thread caller = Thread.currentThread();
            OpaAsyncExecutor.Preparation work = snapshot -> () -> {
                assertThat(Thread.currentThread()).isNotSameAs(caller);
                entered.countDown();
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                snapshot.getMessage().setHeader("completed", true);
                return CompletableFuture.completedFuture(null);
            };
            Exchange first = new DefaultExchange(context);
            Exchange second = new DefaultExchange(context);
            try {
                assertThat(executor.process(first, doneSync -> {
                    count.incrementAndGet();
                    callbacks.countDown();
                }, work)).isFalse();
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(executor.process(second, doneSync -> {
                    count.incrementAndGet();
                    callbacks.countDown();
                }, work)).isFalse();
                Exchange rejected = new DefaultExchange(context);
                AtomicInteger rejectionCallbacks = new AtomicInteger();
                assertThat(executor.process(rejected, doneSync -> {
                    assertThat(doneSync).isTrue();
                    rejectionCallbacks.incrementAndGet();
                }, work)).isTrue();
                assertThat(rejected.getException()).isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
                assertThat(rejectionCallbacks).hasValue(1);
            } finally {
                release.countDown();
            }
            assertThat(callbacks.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(count).hasValue(2);
            assertThat(first.getMessage().getHeader("completed")).isEqualTo(true);
            assertThat(second.getMessage().getHeader("completed")).isEqualTo(true);
        }
    }

    @Test
    void deadlineCompletesOnceAndLateWorkerCannotMutateTheCaller() throws Exception {
        try (var context = new DefaultCamelContext(); var executor = new OpaAsyncExecutor(context, this, 1, 1, 300)) {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch workerDone = new CountDownLatch(1);
            CountDownLatch callback = new CountDownLatch(1);
            AtomicInteger calls = new AtomicInteger();
            Exchange exchange = new DefaultExchange(context);
            try {
                executor.process(exchange, doneSync -> {
                    calls.incrementAndGet();
                    callback.countDown();
                }, snapshot -> () -> {
                    entered.countDown();
                    awaitIgnoringInterrupt(release);
                    snapshot.getMessage().setHeader("late", "must-not-escape");
                    workerDone.countDown();
                    return CompletableFuture.completedFuture(null);
                });
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(callback.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(exchange.getException()).isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally {
                release.countDown();
            }
            assertThat(workerDone.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(exchange.getMessage().getHeader("late")).isNull();
            assertThat(calls).hasValue(1);
        }
    }

    @Test
    void stopCancelsRunningAndQueuedWorkExactlyOnce() throws Exception {
        try (var context = new DefaultCamelContext(); var executor = new OpaAsyncExecutor(context, this, 1, 1, 5000)) {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch callbacks = new CountDownLatch(2);
            AtomicInteger calls = new AtomicInteger();
            OpaAsyncExecutor.Preparation work = snapshot -> () -> {
                entered.countDown();
                awaitIgnoringInterrupt(release);
                return CompletableFuture.completedFuture(null);
            };
            Exchange first = new DefaultExchange(context);
            Exchange second = new DefaultExchange(context);
            try {
                executor.process(first, doneSync -> {
                    calls.incrementAndGet();
                    callbacks.countDown();
                }, work);
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                executor.process(second, doneSync -> {
                    calls.incrementAndGet();
                    callbacks.countDown();
                }, work);
                executor.close();
                assertThat(callbacks.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(first.getException()).isInstanceOf(java.util.concurrent.CancellationException.class);
                assertThat(second.getException()).isInstanceOf(java.util.concurrent.CancellationException.class);
                assertThat(calls).hasValue(2);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void snapshotsContainerValuesWithoutCompletingTheOriginalUnitOfWork() throws Exception {
        try (var context = new DefaultCamelContext(); var executor = new OpaAsyncExecutor(context, this, 1, 1, 5000)) {
            Exchange exchange = new DefaultExchange(context);
            var originalUnitOfWork = org.mockito.Mockito.mock(org.apache.camel.spi.UnitOfWork.class);
            exchange.getExchangeExtension().setUnitOfWork(originalUnitOfWork);
            List<String> body = new ArrayList<>(List.of("before"));
            exchange.getMessage().setBody(Map.of("items", body));
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch callback = new CountDownLatch(1);
            try {
                executor.process(exchange, doneSync -> callback.countDown(), snapshot -> () -> {
                    assertThat(snapshot.getUnitOfWork()).isNull();
                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThat(snapshot.getMessage().getBody(Map.class)).containsEntry("items", List.of("before"));
                    return CompletableFuture.completedFuture(null);
                });
                body.add("after");
            } finally {
                release.countDown();
            }
            assertThat(callback.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(exchange.getException()).isNull();
            assertThat(exchange.getUnitOfWork()).isSameAs(originalUnitOfWork);
            org.mockito.Mockito.verify(originalUnitOfWork, org.mockito.Mockito.never()).done(exchange);
        }
    }

    @Test
    void blockedRoutingCallbackCannotStallAnotherAuthorizationDeadline() throws Exception {
        try (var context = new DefaultCamelContext(); var executor = new OpaAsyncExecutor(context, this, 1, 2, 200)) {
            CountDownLatch callbackEntered = new CountDownLatch(1);
            CountDownLatch releaseCallback = new CountDownLatch(1);
            CountDownLatch secondCallback = new CountDownLatch(1);
            Exchange first = new DefaultExchange(context);
            Exchange second = new DefaultExchange(context);
            try {
                executor.process(first, doneSync -> {
                    callbackEntered.countDown();
                    try {
                        assertThat(releaseCallback.await(5, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, snapshot -> CompletableFuture::new);
                assertThat(callbackEntered.await(5, TimeUnit.SECONDS)).isTrue();
                executor.process(second, doneSync -> secondCallback.countDown(), snapshot -> CompletableFuture::new);
                assertThat(secondCallback.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(second.getException()).isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally {
                releaseCallback.countDown();
            }
        }
    }

    @Test
    void resultCopyFailureStillCompletesTheCallbackExactlyOnce() throws Exception {
        try (var context = new DefaultCamelContext(); var executor = new OpaAsyncExecutor(context, this, 1, 1, 1000)) {
            Exchange exchange = new DefaultExchange(context);
            exchange.setIn(new org.apache.camel.support.DefaultMessage(context) {
                @Override
                public void copyFrom(org.apache.camel.Message source) {
                    throw new IllegalStateException("Result copy failed");
                }
            });
            CountDownLatch callback = new CountDownLatch(1);
            AtomicInteger calls = new AtomicInteger();
            executor.process(exchange, doneSync -> {
                calls.incrementAndGet();
                callback.countDown();
            },
                    snapshot -> () -> CompletableFuture.completedFuture(null));
            assertThat(callback.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(calls).hasValue(1);
            assertThat(exchange.getException()).isInstanceOf(IllegalStateException.class).hasMessage("Result copy failed");
        }
    }

    @Test
    void virtualThreadFactoryCannotExceedWorkerBoundOrBlockSubmissionAfterTimeout() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getExecutorServiceManager().setThreadPoolFactory(new org.apache.camel.support.DefaultThreadPoolFactory() {
                @Override
                public java.util.concurrent.ExecutorService newThreadPool(
                        org.apache.camel.spi.ThreadPoolProfile profile,
                        java.util.concurrent.ThreadFactory factory) {
                    return super.newThreadPool(profile, new org.apache.camel.util.concurrent.ThreadFactoryTypeAware() {
                        @Override
                        public boolean isVirtual() {
                            return true;
                        }

                        @Override
                        public Thread newThread(Runnable task) {
                            return Thread.ofVirtual().unstarted(task);
                        }
                    });
                }
            });
            try (var executor = new OpaAsyncExecutor(context, this, 1, 1, 200)) {
                CountDownLatch entered = new CountDownLatch(1);
                CountDownLatch release = new CountDownLatch(1);
                CountDownLatch firstDone = new CountDownLatch(1);
                AtomicInteger actualInvocations = new AtomicInteger();
                OpaAsyncExecutor.Preparation work = snapshot -> () -> {
                    actualInvocations.incrementAndGet();
                    entered.countDown();
                    awaitIgnoringInterrupt(release);
                    return CompletableFuture.completedFuture(null);
                };
                try {
                    executor.process(new DefaultExchange(context), doneSync -> firstDone.countDown(), work);
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThat(firstDone.await(2, TimeUnit.SECONDS)).isTrue();
                    for (int index = 0; index < 3; index++) {
                        CountDownLatch callback = new CountDownLatch(1);
                        var submitted = CompletableFuture.supplyAsync(() -> executor.process(new DefaultExchange(context),
                                doneSync -> callback.countDown(), work));
                        submitted.get(1, TimeUnit.SECONDS);
                        assertThat(callback.await(2, TimeUnit.SECONDS)).isTrue();
                    }
                    assertThat(actualInvocations).hasValue(1);
                } finally {
                    release.countDown();
                }
            }
        }
    }

    private static void awaitIgnoringInterrupt(CountDownLatch latch) throws Exception {
        boolean interrupted = false;
        try {
            for (;;) {
                try {
                    if (!latch.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Test did not release work");
                    }
                    return;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
