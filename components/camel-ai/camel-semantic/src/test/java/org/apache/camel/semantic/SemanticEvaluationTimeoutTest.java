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
package org.apache.camel.semantic;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.console.DevConsole;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultExecutorServiceManager;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.concurrent.BoundedExecutorService;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SemanticEvaluationTimeoutTest {
    @Test
    void timeoutInterruptsTheProviderAndReturnsAnError() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new WaitingExpert();
            context.getRegistry().bind("waiting", expert);
            context.start();
            var console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("semantic-evaluate");
            JsonObject result = (JsonObject) console.call(DevConsole.MediaType.JSON, options(500));
            assertThat(result).containsEntry("status", "failed");
            assertThat(result.getString("error")).contains("timed out", "cancellation requested");
            assertThat(expert.entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(expert.interrupted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat((JsonObject) console.call(DevConsole.MediaType.JSON, options(50001)))
                    .containsEntry("status", "failed");
        }
    }

    @Test
    void interruptingTheCallerCancelsItsEvaluation() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new WaitingExpert();
            context.getRegistry().bind("waiting", expert);
            context.start();
            var console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("semantic-evaluate");
            FutureTask<Object> call = new FutureTask<>(() -> console.call(DevConsole.MediaType.JSON, options(50000)));
            Thread caller = new Thread(call);
            caller.start();
            try {
                assertThat(expert.entered.await(5, TimeUnit.SECONDS)).isTrue();
                call.cancel(true);
                assertThat(expert.interrupted.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                call.cancel(true);
                caller.join(5000);
            }
        }
    }

    @Test
    void concurrentCallsHaveNoUnboundedQueue() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new WaitingExpert(2);
            context.getRegistry().bind("waiting", expert);
            context.start();
            var console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("semantic-evaluate");
            FutureTask<Object> first = new FutureTask<>(() -> console.call(DevConsole.MediaType.JSON, options(50000)));
            FutureTask<Object> second = new FutureTask<>(() -> console.call(DevConsole.MediaType.JSON, options(50000)));
            Thread a = new Thread(first);
            Thread b = new Thread(second);
            a.start();
            b.start();
            try {
                assertThat(expert.entered.await(5, TimeUnit.SECONDS)).isTrue();
                JsonObject response = (JsonObject) console.call(DevConsole.MediaType.JSON, options(500));
                assertThat(response).containsEntry("status", "failed");
                assertThat(response.getString("error")).contains("Busy");
            } finally {
                first.cancel(true);
                second.cancel(true);
                a.join(5000);
                b.join(5000);
            }
        }
    }

    @Test
    void preStartCancellationReleasesConcurrencySlotAndPurgesQueue() throws Exception {
        try (var context = new DefaultCamelContext()) {
            BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(2);
            context.setExecutorServiceManager(new DefaultExecutorServiceManager(context) {
                @Override
                public ExecutorService newThreadPool(Object source, String name, ThreadPoolProfile profile) {
                    if ("SemanticEvaluation".equals(name)) {
                        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, queue);
                    }
                    return super.newThreadPool(source, name, profile);
                }
            });
            CountDownLatch task1Entered = new CountDownLatch(1);
            CountDownLatch releaseTask1 = new CountDownLatch(1);
            AtomicInteger expertCalls = new AtomicInteger();
            context.getRegistry().bind("waiting", new SemanticMetadataConsoleTest.Detector() {
                @Override
                public void validate(SemanticEvaluation evaluation) {
                }

                @Override
                public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
                    expertCalls.incrementAndGet();
                    if (task1Entered.getCount() > 0) {
                        task1Entered.countDown();
                        try {
                            releaseTask1.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return new SemanticResult(false, null, null, null, null);
                }
            });
            context.start();
            var console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("semantic-evaluate");
            FutureTask<Object> task1 = new FutureTask<>(() -> console.call(DevConsole.MediaType.JSON, options(50000)));
            Thread thread1 = new Thread(task1);
            thread1.start();
            try {
                assertThat(task1Entered.await(5, TimeUnit.SECONDS)).isTrue();
                // Task 2 is submitted while worker is busy with Task 1; Task 2 is queued before starting
                JsonObject timeoutResult = (JsonObject) console.call(DevConsole.MediaType.JSON, options(50));
                assertThat(timeoutResult).containsEntry("status", "failed");
                assertThat(timeoutResult.getString("error")).contains("timed out", "cancellation requested");

                // Verify that while Task 1 is still held, cancelled Task 2 has been purged from the queue
                await().atMost(5, TimeUnit.SECONDS).until(queue::isEmpty);

                // Pre-start cancellation must have released the slot and purged queue, so Task 3 can be queued while Task 1 is still held
                FutureTask<Object> task3 = new FutureTask<>(() -> console.call(DevConsole.MediaType.JSON, options(50000)));
                Thread thread3 = new Thread(task3);
                thread3.start();
                try {
                    // Task 3 is visibly queued while Task 1 is still held
                    await().atMost(5, TimeUnit.SECONDS).until(() -> queue.size() == 1);

                    // Release task 1 so the worker becomes free to execute task 3
                    releaseTask1.countDown();
                    JsonObject task3Result = (JsonObject) task3.get(5, TimeUnit.SECONDS);
                    assertThat(task3Result).containsEntry("status", "success");
                    // Expert was called for task 1 and task 3, never for cancelled task 2
                    assertThat(expertCalls).hasValue(2);
                } finally {
                    task3.cancel(true);
                    thread3.join(5000);
                }
            } finally {
                releaseTask1.countDown();
                task1.cancel(true);
                thread1.join(5000);
            }
        }
    }

    @Test
    void virtualThreadSubmissionDoesNotBlockBeyondDeadline() throws Exception {
        try (var context = new DefaultCamelContext()) {
            CountDownLatch task1Entered = new CountDownLatch(1);
            CountDownLatch releaseTask1 = new CountDownLatch(1);
            BoundedExecutorService[] boundedExec = new BoundedExecutorService[1];
            context.setExecutorServiceManager(new DefaultExecutorServiceManager(context) {
                @Override
                public ExecutorService newThreadPool(Object source, String name, ThreadPoolProfile profile) {
                    if ("SemanticEvaluation".equals(name)) {
                        boundedExec[0] = new BoundedExecutorService(
                                Executors.newCachedThreadPool(),
                                1, profile.getKeepAliveTime(), profile.getTimeUnit(), false, profile.getRejectedPolicy());
                        return boundedExec[0];
                    }
                    return super.newThreadPool(source, name, profile);
                }
            });
            context.getRegistry().bind("waiting", new SemanticMetadataConsoleTest.Detector() {
                @Override
                public void validate(SemanticEvaluation evaluation) {
                }

                @Override
                public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
                    task1Entered.countDown();
                    try {
                        releaseTask1.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new SemanticResult(false, null, null, null, null);
                }
            });
            context.start();
            var console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("semantic-evaluate");
            FutureTask<Object> task1 = new FutureTask<>(() -> console.call(DevConsole.MediaType.JSON, options(50000)));
            Thread thread1 = new Thread(task1);
            thread1.start();
            try {
                assertThat(task1Entered.await(5, TimeUnit.SECONDS)).isTrue();
                long callStart = System.nanoTime();
                // Task 2 submitted with a 50ms timeout while Task 1 is held. It must return promptly with timeout failure.
                JsonObject result = (JsonObject) console.call(DevConsole.MediaType.JSON, options(50));
                long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - callStart);
                assertThat(durationMillis).isLessThan(2000);
                assertThat(result).containsEntry("status", "failed");
                assertThat(result.getString("error")).contains("timed out", "cancellation requested");

                // Task 3 submitted with a 5000ms timeout while Task 1 is still held. It retries and succeeds once permit is freed.
                long rejectionsBeforeTask3 = boundedExec[0].getRejectedCount();
                FutureTask<Object> task3 = new FutureTask<>(() -> console.call(DevConsole.MediaType.JSON, options(5000)));
                Thread thread3 = new Thread(task3);
                thread3.start();
                try {
                    // Task 3 encounters transient permit rejection while task 1 is held
                    await().atMost(5, TimeUnit.SECONDS).until(() -> boundedExec[0].getRejectedCount() > rejectionsBeforeTask3);
                    // Release task 1 so permit becomes available for task 3
                    releaseTask1.countDown();
                    JsonObject task3Result = (JsonObject) task3.get(5, TimeUnit.SECONDS);
                    assertThat(task3Result).containsEntry("status", "success");
                } finally {
                    task3.cancel(true);
                    thread3.join(5000);
                }
            } finally {
                releaseTask1.countDown();
                task1.cancel(true);
                thread1.join(5000);
            }
        }
    }

    @Test
    void callerInterruptedDuringSubmissionRetryCancelsPromptlyAndPreservesInterruptFlag() throws Exception {
        try (var context = new DefaultCamelContext()) {
            CountDownLatch task1Entered = new CountDownLatch(1);
            CountDownLatch releaseTask1 = new CountDownLatch(1);
            BoundedExecutorService[] boundedExec = new BoundedExecutorService[1];
            context.setExecutorServiceManager(new DefaultExecutorServiceManager(context) {
                @Override
                public ExecutorService newThreadPool(Object source, String name, ThreadPoolProfile profile) {
                    if ("SemanticEvaluation".equals(name)) {
                        boundedExec[0] = new BoundedExecutorService(
                                Executors.newCachedThreadPool(),
                                1, profile.getKeepAliveTime(), profile.getTimeUnit(), false, profile.getRejectedPolicy());
                        return boundedExec[0];
                    }
                    return super.newThreadPool(source, name, profile);
                }
            });
            context.getRegistry().bind("waiting", new SemanticMetadataConsoleTest.Detector() {
                @Override
                public void validate(SemanticEvaluation evaluation) {
                }

                @Override
                public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
                    task1Entered.countDown();
                    try {
                        releaseTask1.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new SemanticResult(false, null, null, null, null);
                }
            });
            context.start();
            var console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("semantic-evaluate");
            FutureTask<Object> task1 = new FutureTask<>(() -> console.call(DevConsole.MediaType.JSON, options(50000)));
            Thread thread1 = new Thread(task1);
            thread1.start();
            try {
                assertThat(task1Entered.await(5, TimeUnit.SECONDS)).isTrue();
                AtomicBoolean interruptedAfterCall = new AtomicBoolean();
                FutureTask<Object> task2 = new FutureTask<>(() -> {
                    Object res = console.call(DevConsole.MediaType.JSON, options(10000));
                    if (Thread.currentThread().isInterrupted()) {
                        interruptedAfterCall.set(true);
                    }
                    return res;
                });
                Thread thread2 = new Thread(task2);
                thread2.start();
                try {
                    // Wait until task 2 enters submission retry loop and is rejected at least once
                    await().atMost(5, TimeUnit.SECONDS).until(() -> boundedExec[0].getRejectedCount() > 0);
                    // Interrupt thread 2 while retrying inside submission loop
                    thread2.interrupt();
                    thread2.join(2000);
                    assertThat(thread2.isAlive()).isFalse();
                    assertThat(interruptedAfterCall.get()).isTrue();
                    JsonObject result = (JsonObject) task2.get();
                    assertThat(result).containsEntry("status", "failed");
                    assertThat(result.getString("error")).contains("cancelled");
                } finally {
                    task2.cancel(true);
                    thread2.join(5000);
                }
            } finally {
                releaseTask1.countDown();
                task1.cancel(true);
                thread1.join(5000);
            }
        }
    }

    @Test
    void consoleStopRestartWhileCallersRetryingCancelsRetryingCallersAndAcceptsFreshCall() throws Exception {
        try (var context = new DefaultCamelContext()) {
            CountDownLatch task1Entered = new CountDownLatch(1);
            CountDownLatch releaseTask1 = new CountDownLatch(1);
            BoundedExecutorService[] boundedExec = new BoundedExecutorService[1];
            context.setExecutorServiceManager(new DefaultExecutorServiceManager(context) {
                @Override
                public ExecutorService newThreadPool(Object source, String name, ThreadPoolProfile profile) {
                    if ("SemanticEvaluation".equals(name)) {
                        boundedExec[0] = new BoundedExecutorService(
                                Executors.newCachedThreadPool(),
                                1, profile.getKeepAliveTime(), profile.getTimeUnit(), false, profile.getRejectedPolicy());
                        return boundedExec[0];
                    }
                    return super.newThreadPool(source, name, profile);
                }
            });
            context.getRegistry().bind("waiting", new SemanticMetadataConsoleTest.Detector() {
                @Override
                public void validate(SemanticEvaluation evaluation) {
                }

                @Override
                public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
                    task1Entered.countDown();
                    try {
                        releaseTask1.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new SemanticResult(false, null, null, null, null);
                }
            });
            context.start();
            var console = (SemanticEvaluateConsole) PluginHelper.getDevConsoleResolver(context)
                    .resolveDevConsole("semantic-evaluate");
            FutureTask<Object> task1 = new FutureTask<>(() -> console.call(DevConsole.MediaType.JSON, options(50000)));
            Thread thread1 = new Thread(task1);
            thread1.start();
            try {
                assertThat(task1Entered.await(5, TimeUnit.SECONDS)).isTrue();
                FutureTask<Object> task2 = new FutureTask<>(() -> console.call(DevConsole.MediaType.JSON, options(10000)));
                Thread thread2 = new Thread(task2);
                thread2.start();
                try {
                    // Wait until task 2 enters submission retry loop and is rejected at least once
                    await().atMost(5, TimeUnit.SECONDS).until(() -> boundedExec[0].getRejectedCount() > 0);
                    // Stop console while caller is retrying
                    console.stop();
                    thread2.join(2000);
                    assertThat(thread2.isAlive()).isFalse();
                    JsonObject result2 = (JsonObject) task2.get();
                    assertThat(result2).containsEntry("status", "failed");
                    assertThat(result2.getString("error")).contains("cancelled");

                    // Release task 1 and restart console
                    releaseTask1.countDown();
                    task1.cancel(true);
                    thread1.join(5000);

                    console.start();
                    JsonObject freshResult = (JsonObject) console.call(DevConsole.MediaType.JSON, options(5000));
                    assertThat(freshResult).containsEntry("status", "success");
                } finally {
                    task2.cancel(true);
                    thread2.join(5000);
                }
            } finally {
                releaseTask1.countDown();
                task1.cancel(true);
                thread1.join(5000);
            }
        }
    }

    @Test
    void workerStartingAfterDeadlineReturnsTimeoutResponse() throws Exception {
        try (var context = new DefaultCamelContext()) {
            AtomicInteger expertCalls = new AtomicInteger();
            CountDownLatch callerEnteredGet = new CountDownLatch(1);
            CountDownLatch workerFinished = new CountDownLatch(1);
            context.setExecutorServiceManager(new DefaultExecutorServiceManager(context) {
                @Override
                public ExecutorService newThreadPool(Object source, String name, ThreadPoolProfile profile) {
                    if ("SemanticEvaluation".equals(name)) {
                        return new ThreadPoolExecutor(
                                1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(2)) {
                            @Override
                            public <T> Future<T> submit(Callable<T> task) {
                                FutureTask<T> ft = new FutureTask<>(task);
                                long submitTime = System.nanoTime();
                                execute(() -> {
                                    try {
                                        // Ensure caller has entered task.get()
                                        callerEnteredGet.await(5, TimeUnit.SECONDS);
                                        // Wait until the 50ms deadline has passed before running callable
                                        await().atMost(5, TimeUnit.SECONDS)
                                                .until(() -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - submitTime)
                                                             >= 60);
                                        ft.run();
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    } finally {
                                        workerFinished.countDown();
                                    }
                                });
                                return new Future<T>() {
                                    @Override
                                    public boolean cancel(boolean mayInterruptIfRunning) {
                                        return ft.cancel(mayInterruptIfRunning);
                                    }

                                    @Override
                                    public boolean isCancelled() {
                                        return ft.isCancelled();
                                    }

                                    @Override
                                    public boolean isDone() {
                                        return ft.isDone();
                                    }

                                    @Override
                                    public T get() throws InterruptedException, ExecutionException {
                                        return ft.get();
                                    }

                                    @Override
                                    public T get(long timeout, TimeUnit unit)
                                            throws InterruptedException, ExecutionException, TimeoutException {
                                        callerEnteredGet.countDown();
                                        try {
                                            if (!workerFinished.await(5, TimeUnit.SECONDS)) {
                                                throw new TimeoutException("Worker timed out");
                                            }
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                            throw new ExecutionException(e);
                                        }
                                        return ft.get(timeout, unit);
                                    }
                                };
                            }
                        };
                    }
                    return super.newThreadPool(source, name, profile);
                }
            });
            context.getRegistry().bind("waiting", new SemanticMetadataConsoleTest.Detector() {
                @Override
                public void validate(SemanticEvaluation evaluation) {
                }

                @Override
                public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
                    expertCalls.incrementAndGet();
                    return new SemanticResult(false, null, null, null, null);
                }
            });
            context.start();
            var console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("semantic-evaluate");
            JsonObject jsonResult = (JsonObject) console.call(DevConsole.MediaType.JSON, options(50));
            assertThat(jsonResult).isNotNull();
            assertThat(jsonResult).containsEntry("status", "failed");
            assertThat(jsonResult.getString("error")).contains("timed out", "cancellation requested");
            assertThat(expertCalls).hasValue(0);
        }
    }

    @Test
    void stoppingConsoleWakesQueuedCallersImmediately() throws Exception {
        try (var context = new DefaultCamelContext()) {
            BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(2);
            context.setExecutorServiceManager(new DefaultExecutorServiceManager(context) {
                @Override
                public ExecutorService newThreadPool(Object source, String name, ThreadPoolProfile profile) {
                    if ("SemanticEvaluation".equals(name)) {
                        return new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, queue);
                    }
                    return super.newThreadPool(source, name, profile);
                }
            });
            CountDownLatch task1Entered = new CountDownLatch(1);
            CountDownLatch releaseTask1 = new CountDownLatch(1);
            context.getRegistry().bind("waiting", new SemanticMetadataConsoleTest.Detector() {
                @Override
                public void validate(SemanticEvaluation evaluation) {
                }

                @Override
                public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
                    task1Entered.countDown();
                    try {
                        releaseTask1.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new SemanticResult(false, null, null, null, null);
                }
            });
            context.start();
            var console = (SemanticEvaluateConsole) PluginHelper.getDevConsoleResolver(context)
                    .resolveDevConsole("semantic-evaluate");
            FutureTask<Object> task1 = new FutureTask<>(() -> console.call(DevConsole.MediaType.JSON, options(50000)));
            Thread thread1 = new Thread(task1);
            thread1.start();
            try {
                assertThat(task1Entered.await(5, TimeUnit.SECONDS)).isTrue();
                // Task 2 is queued because worker 1 is busy
                FutureTask<Object> task2 = new FutureTask<>(() -> console.call(DevConsole.MediaType.JSON, options(50000)));
                Thread thread2 = new Thread(task2);
                thread2.start();
                try {
                    // Ensure task 2 has reached the executor queue before stopping
                    await().atMost(5, TimeUnit.SECONDS).until(() -> queue.size() > 0);
                    // Stop console; dropped task 2 must be cancelled and wake thread 2 immediately
                    console.stop();
                    thread2.join(2000);
                    assertThat(thread2.isAlive()).isFalse();
                    JsonObject task2Result = (JsonObject) task2.get();
                    assertThat(task2Result).containsEntry("status", "failed");
                } finally {
                    task2.cancel(true);
                    thread2.join(5000);
                }
            } finally {
                releaseTask1.countDown();
                task1.cancel(true);
                thread1.join(5000);
            }
        }
    }

    @Test
    void oldEvaluationFinishingAfterStopStartMaintainsSlotAccounting() throws Exception {
        try (var context = new DefaultCamelContext()) {
            CountDownLatch oldEntered = new CountDownLatch(1);
            CountDownLatch finishOld = new CountDownLatch(1);
            CountDownLatch newEntered = new CountDownLatch(1);
            context.getRegistry().bind("waiting", new SemanticMetadataConsoleTest.Detector() {
                @Override
                public void validate(SemanticEvaluation evaluation) {
                }

                @Override
                public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
                    if (oldEntered.getCount() > 0) {
                        oldEntered.countDown();
                        while (finishOld.getCount() > 0) {
                            try {
                                finishOld.await(5, TimeUnit.SECONDS);
                            } catch (InterruptedException ignored) {
                            }
                        }
                    } else {
                        newEntered.countDown();
                        try {
                            new CountDownLatch(1).await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return new SemanticResult(false, null, null, null, null);
                }
            });
            context.start();
            var console = (SemanticEvaluateConsole) PluginHelper.getDevConsoleResolver(context)
                    .resolveDevConsole("semantic-evaluate");
            FutureTask<Object> oldTask = new FutureTask<>(() -> console.call(DevConsole.MediaType.JSON, options(50000)));
            Thread oldThread = new Thread(oldTask);
            oldThread.start();
            try {
                assertThat(oldEntered.await(5, TimeUnit.SECONDS)).isTrue();
                // Stop console while old task is still running
                console.stop();
                // Restart console
                console.start();
                // Old task is still in-flight holding 1 slot; start another task on restarted console
                FutureTask<Object> newTask = new FutureTask<>(() -> console.call(DevConsole.MediaType.JSON, options(50000)));
                Thread newThread = new Thread(newTask);
                newThread.start();
                try {
                    assertThat(newEntered.await(5, TimeUnit.SECONDS)).isTrue();
                    // With 2 active tasks (old + new), a third concurrent call is rejected as Busy
                    JsonObject busyResult = (JsonObject) console.call(DevConsole.MediaType.JSON, options(500));
                    assertThat(busyResult).containsEntry("status", "failed");
                    assertThat(busyResult.getString("error")).contains("Busy");
                } finally {
                    newTask.cancel(true);
                    newThread.join(5000);
                }
            } finally {
                finishOld.countDown();
                oldTask.cancel(true);
                oldThread.join(5000);
            }
        }
    }

    private static Map<String, Object> options(long timeout) {
        return Map.of("expert", "waiting", "operation", "injection", "input", "test", "timeout", timeout);
    }

    static class WaitingExpert extends SemanticMetadataConsoleTest.Detector {
        final CountDownLatch entered;

        WaitingExpert() {
            this(1);
        }

        WaitingExpert(int callers) {
            entered = new CountDownLatch(callers);
        }

        final CountDownLatch interrupted = new CountDownLatch(1);

        @Override
        public void validate(SemanticEvaluation evaluation) {
        }

        @Override
        public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
            entered.countDown();
            try {
                new CountDownLatch(1).await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return new SemanticResult(false, null, null, null, null);
        }
    }
}
