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
package org.apache.camel.component.a2a;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.a2a.model.SendMessageResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.state.InMemoryTaskStore;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The task permits (maxConcurrentTasks) and the pending task queue (taskQueueSize) of async tasks (returnImmediately).
 * Each test forces one interleaving of the request thread, the async worker, the async timeout and a cancel with a hook
 * on the queue, the executor or the in-flight map of the consumer; a hook only holds one thread at one step, so each
 * ordering is one the consumer allows without it.
 */
class A2AConsumerTaskPermitsTest {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private CamelContext context;
    private A2AConsumer consumer;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        NoopRestConsumerFactory.bindTo(context);
        context.getRestConfiguration().setComponent("noopRestConsumerFactory");
        context.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (consumer != null) {
            consumer.doStop();
        }
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void followUpMessageOnARunningTaskGivesBothPermitsBack() throws Exception {
        CountDownLatch release1 = new CountDownLatch(1);
        AtomicInteger routeCalls = new AtomicInteger();
        consumer = createConsumer(2, 0, 0, exchange -> {
            if (routeCalls.incrementAndGet() == 1) {
                release1.await(10, TimeUnit.SECONDS);
            }
        });
        Semaphore permits = getField(consumer, "taskPermits");
        Map<?, ?> inFlight = getField(consumer, "inFlightTasks");

        String task1 = sendAsync();
        // a follow-up message on task 1 while its first message is still being routed
        Exchange followUp = sendAsyncExchange("\"taskId\":\"" + task1 + "\",");
        assertThat(followUp.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isNull();
        assertThat(permits.availablePermits()).isZero();
        release1.countDown();

        await().atMost(10, TimeUnit.SECONDS).until(() -> routeCalls.get() == 2 && inFlight.isEmpty());
        await().atMost(5, TimeUnit.SECONDS).until(() -> permits.availablePermits() == 2);
        // the agent still runs two tasks at once
        sendAsync();
        sendAsync();
        await().atMost(10, TimeUnit.SECONDS).until(() -> routeCalls.get() == 4 && permits.availablePermits() == 2);
    }

    @Test
    void timeoutFiringAsTheTaskCompletesGivesThePermitBack() throws Exception {
        CountDownLatch routeMayEnd = new CountDownLatch(1);
        AtomicInteger routeCalls = new AtomicInteger();
        consumer = createConsumer(1, 1, 200, exchange -> {
            if (routeCalls.incrementAndGet() == 1) {
                routeMayEnd.await(10, TimeUnit.SECONDS);
            }
        });
        TrackingExecutor exec = installTrackingExecutor(consumer);
        AtomicBoolean armed = new AtomicBoolean(true);
        // the timeout takes the task out of the in-flight map; right then the task completes
        ConcurrentHashMap<String, Object> inFlight = new ConcurrentHashMap<>() {
            @Override
            public Object remove(Object key) {
                Object removed = super.remove(key);
                completeTaskIfTimeoutThread();
                return removed;
            }

            @Override
            public boolean remove(Object key, Object value) {
                boolean removed = super.remove(key, value);
                completeTaskIfTimeoutThread();
                return removed;
            }

            private void completeTaskIfTimeoutThread() {
                if (Thread.currentThread().getName().contains("A2AAsyncTimeout") && armed.compareAndSet(true, false)) {
                    routeMayEnd.countDown();
                    exec.awaitCompletions(1);
                }
            }
        };
        setField(consumer, "inFlightTasks", inFlight);
        Semaphore permits = getField(consumer, "taskPermits");

        String task1 = sendAsync();
        await().atMost(10, TimeUnit.SECONDS).until(() -> !armed.get() && inFlight.isEmpty());
        await().atMost(5, TimeUnit.SECONDS).until(() -> permits.availablePermits() == 1);
        assertThat(state(task1)).isIn(TaskState.COMPLETED, TaskState.FAILED);

        // the agent is not at capacity: the next task runs
        String task2 = sendAsync();
        await().atMost(10, TimeUnit.SECONDS).until(() -> state(task2) == TaskState.COMPLETED);
        assertThat(routeCalls.get()).isEqualTo(2);
    }

    @Test
    void taskQueuedWhileTheRunningTaskEndsIsStarted() throws Exception {
        CountDownLatch release1 = new CountDownLatch(1);
        AtomicInteger routeCalls = new AtomicInteger();
        consumer = createConsumer(1, 1, 0, exchange -> {
            if (routeCalls.incrementAndGet() == 1) {
                release1.await(10, TimeUnit.SECONDS);
            }
        });
        TrackingExecutor exec = installTrackingExecutor(consumer);
        AtomicBoolean armed = new AtomicBoolean(true);
        LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>(1) {
            @Override
            public boolean offer(Object e) {
                if (armed.compareAndSet(true, false)) {
                    // the request found no free permit; before it queues its task, task 1 ends, gives its permit back
                    // and finds the queue empty
                    release1.countDown();
                    exec.awaitCompletions(1);
                }
                return super.offer(e);
            }
        };
        setField(consumer, "pendingTaskQueue", queue);
        Semaphore permits = getField(consumer, "taskPermits");

        String task1 = sendAsync();
        String task2 = sendAsync();

        await().atMost(10, TimeUnit.SECONDS).until(() -> state(task2) == TaskState.COMPLETED);
        assertThat(state(task1)).isEqualTo(TaskState.COMPLETED);
        assertThat(routeCalls.get()).isEqualTo(2);
        assertThat(queue).isEmpty();
        await().atMost(5, TimeUnit.SECONDS).until(() -> permits.availablePermits() == 1);
    }

    @Test
    void queuedTaskCanceledWhileTheDrainHoldsItDoesNotRun() throws Exception {
        CountDownLatch release1 = new CountDownLatch(1);
        CountDownLatch polled = new CountDownLatch(1);
        CountDownLatch cancelReturned = new CountDownLatch(1);
        List<String> routeTaskIds = new CopyOnWriteArrayList<>();
        consumer = createConsumer(1, 1, 0, exchange -> {
            routeTaskIds.add(exchange.getProperty(A2AConstants.REQUEST_TASK_ID, String.class));
            if (routeTaskIds.size() == 1) {
                release1.await(10, TimeUnit.SECONDS);
            }
        });
        TrackingExecutor exec = installTrackingExecutor(consumer);
        AtomicBoolean armed = new AtomicBoolean(true);
        LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>(1) {
            @Override
            public Object poll() {
                Object e = super.poll();
                if (e != null && armed.compareAndSet(true, false)) {
                    // the drain took the task out of the queue and has not submitted it yet: the cancel runs now
                    polled.countDown();
                    try {
                        cancelReturned.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
                return e;
            }
        };
        setField(consumer, "pendingTaskQueue", queue);
        Semaphore permits = getField(consumer, "taskPermits");

        String task1 = sendAsync();
        String task2 = sendAsync();
        assertThat(state(task2)).isEqualTo(TaskState.SUBMITTED);
        assertThat(queue).hasSize(1);

        release1.countDown();
        assertThat(polled.await(10, TimeUnit.SECONDS)).isTrue();
        Exchange cancel = new DefaultExchange(context);
        cancel.getMessage().setHeader(Exchange.HTTP_PATH, "/tasks/" + task2 + ":cancel");
        consumer.handleCancelTask(cancel);
        Task canceled = OBJECT_MAPPER.readValue(cancel.getMessage().getBody(byte[].class), Task.class);
        assertThat(canceled.status().state()).isEqualTo(TaskState.CANCELED);
        cancelReturned.countDown();

        // the drain submitted the canceled task: it ends without running the route
        exec.awaitCompletions(2);
        assertThat(routeTaskIds).containsExactly(task1);
        assertThat(state(task2)).isEqualTo(TaskState.CANCELED);
        await().atMost(5, TimeUnit.SECONDS).until(() -> permits.availablePermits() == 1);
    }

    @Test
    void queuedTaskThatFailsBeforeTheRequestReturnsEndsFailed() throws Exception {
        CountDownLatch release1 = new CountDownLatch(1);
        AtomicInteger routeCalls = new AtomicInteger();
        consumer = createConsumer(1, 1, 0, exchange -> {
            if (routeCalls.incrementAndGet() == 1) {
                release1.await(10, TimeUnit.SECONDS);
            } else {
                throw new IllegalStateException("route failure for the queued task");
            }
        });
        TrackingExecutor exec = installTrackingExecutor(consumer);
        AtomicBoolean armed = new AtomicBoolean(true);
        LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>(1) {
            @Override
            public boolean offer(Object e) {
                boolean offered = super.offer(e);
                if (offered && armed.compareAndSet(true, false)) {
                    // right after the task is queued, task 1 ends and the drain runs the queued task, which fails,
                    // all before the request returns
                    release1.countDown();
                    exec.awaitCompletions(2);
                }
                return offered;
            }
        };
        setField(consumer, "pendingTaskQueue", queue);
        Semaphore permits = getField(consumer, "taskPermits");

        sendAsync();
        String task2 = sendAsync();

        assertThat(routeCalls.get()).isEqualTo(2);
        assertThat(state(task2)).isEqualTo(TaskState.FAILED);
        await().atMost(5, TimeUnit.SECONDS).until(() -> permits.availablePermits() == 1);
    }

    @Test
    void fullQueueRejectsTheTaskWithoutTouchingTheStore() throws Exception {
        CountDownLatch release1 = new CountDownLatch(1);
        AtomicInteger routeCalls = new AtomicInteger();
        consumer = createConsumer(1, 1, 0, exchange -> {
            if (routeCalls.incrementAndGet() == 1) {
                release1.await(10, TimeUnit.SECONDS);
            }
        });
        Semaphore permits = getField(consumer, "taskPermits");
        InMemoryTaskStore store = (InMemoryTaskStore) consumer.getEndpoint().getTaskStore();

        String task1 = sendAsync();
        // a queued task that is canceled: a terminal task the store keeps
        String task2 = sendAsync();
        Exchange cancel = new DefaultExchange(context);
        cancel.getMessage().setHeader(Exchange.HTTP_PATH, "/tasks/" + task2 + ":cancel");
        consumer.handleCancelTask(cancel);
        String task3 = sendAsync();
        // the store is at its cap: a put of one more task would evict the oldest terminal task (task 2)
        store.setMaxStoredTasks(3);

        Exchange fourth = sendAsyncExchange("");
        assertThat(fourth.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isEqualTo(429);
        assertThat(store.keys()).containsExactlyInAnyOrder(task1, task2, task3);
        assertThat(state(task2)).isEqualTo(TaskState.CANCELED);

        release1.countDown();
        await().atMost(10, TimeUnit.SECONDS).until(() -> state(task3) == TaskState.COMPLETED);
        await().atMost(5, TimeUnit.SECONDS).until(() -> permits.availablePermits() == 1);
    }

    @Test
    void runThatOutlivesAStopGivesItsPermitToTheStartThatGaveIt() throws Exception {
        CountDownLatch release1 = new CountDownLatch(1);
        AtomicInteger routeCalls = new AtomicInteger();
        consumer = createConsumer(2, 0, 0, exchange -> {
            if (routeCalls.incrementAndGet() == 1) {
                awaitIgnoringInterrupts(release1);
            }
        });
        // the stop waits a short time for the run that does not react to the interrupt
        context.getExecutorServiceManager().setShutdownAwaitTermination(200);
        ExecutorService firstExecutor = getField(consumer, "asyncExecutor");

        String task1 = sendAsync();
        // a follow-up message on task 1 replaces the first run in the in-flight map: the stop reaches only the second
        Exchange followUp = sendAsyncExchange("\"taskId\":\"" + task1 + "\",");
        assertThat(followUp.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isNull();

        consumer.doStop();
        consumer.doStart();
        Semaphore permits = getField(consumer, "taskPermits");
        assertThat(permits.availablePermits()).isEqualTo(2);

        // the first run ends after the new start
        release1.countDown();
        assertThat(firstExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(permits.availablePermits()).isEqualTo(2);
    }

    // ---- helpers ----

    private A2AConsumer createConsumer(int maxTasks, int queueSize, long asyncTimeout, Processor processor)
            throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Capacity Agent");
        config.setVersion("1.0.0");
        config.setMaxConcurrentTasks(maxTasks);
        config.setTaskQueueSize(queueSize);
        config.setReturnImmediately(true);
        config.setAsyncTimeout(asyncTimeout);

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);
        A2AEndpoint endpoint = new A2AEndpoint("a2a:capacity-agent", component, config);
        endpoint.setAgentCardSource("capacity-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer answer = new A2AConsumer(endpoint, processor);
        answer.doStart();
        return answer;
    }

    private Exchange sendAsyncExchange(String taskIdField) throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("{\"message\":{\"messageId\":\"msg-" + UUID.randomUUID() + "\"," + taskIdField
                                      + "\"role\":\"user\",\"parts\":[{\"text\":\"Hello\"}]}}");
        consumer.handleSendMessage(exchange);
        return exchange;
    }

    private String sendAsync() throws Exception {
        Exchange exchange = sendAsyncExchange("");
        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isNull();
        SendMessageResponse response
                = OBJECT_MAPPER.readValue(exchange.getMessage().getBody(byte[].class), SendMessageResponse.class);
        return response.getTask().id();
    }

    private TaskState state(String taskId) {
        Task task = consumer.getEndpoint().getTaskStore().get(taskId);
        return task == null ? null : task.status().state();
    }

    private static void awaitIgnoringInterrupts(CountDownLatch latch) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    if (!latch.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("not released");
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

    private static TrackingExecutor installTrackingExecutor(A2AConsumer consumer) throws Exception {
        TrackingExecutor exec = new TrackingExecutor();
        ExecutorService previous = getField(consumer, "asyncExecutor");
        setField(consumer, "asyncExecutor", exec);
        previous.shutdownNow();
        return exec;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) throws Exception {
        Field field = A2AConsumer.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = A2AConsumer.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * Same shape as the async executor of the consumer (one thread, FIFO queue); counts the tasks whose run returned.
     */
    private static final class TrackingExecutor extends ThreadPoolExecutor {
        private final Semaphore completions = new Semaphore(0);

        TrackingExecutor() {
            super(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            completions.release();
        }

        void awaitCompletions(int n) {
            try {
                if (!completions.tryAcquire(n, 10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("tasks did not complete");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
