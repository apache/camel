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
package org.apache.camel.processor.resequencer;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.TestSupport;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ResequencerEngineTest extends TestSupport {

    private static final boolean IGNORE_LOAD_TESTS = Boolean.parseBoolean(System.getProperty("ignore.load.tests", "true"));

    private ResequencerEngineSync<Integer> resequencer;
    private ResequencerRunner<Integer> runner;
    private SequenceBuffer<Integer> buffer;

    @Override
    @AfterEach
    public void tearDown() {
        if (runner != null) {
            runner.cancel();
        }
        if (resequencer != null) {
            resequencer.stop();
        }
    }

    @Test
    void testTimeout1() throws Exception {
        initResequencer(500);
        resequencer.insert(4);
        assertNull(buffer.poll(250));
        assertEquals(4, buffer.take());
        assertEquals(4, resequencer.getLastDelivered());
    }

    @Test
    void testTimeout2() throws Exception {
        initResequencer(500);
        resequencer.setLastDelivered(2);
        resequencer.insert(4);
        assertNull(buffer.poll(250));
        assertEquals(4, buffer.take());
        assertEquals(4, resequencer.getLastDelivered());
    }

    @Test
    void testTimeout3() throws Exception {
        initResequencer(500);
        resequencer.setLastDelivered(3);
        resequencer.insert(4);
        assertEquals(4, buffer.poll(5_000));
        assertEquals(4, resequencer.getLastDelivered());
    }

    @Test
    void testTimeout4() throws Exception {
        initResequencer(500);
        resequencer.setLastDelivered(2);
        resequencer.insert(4);
        resequencer.insert(3);
        assertEquals(3, buffer.poll(5_000));
        assertEquals(4, buffer.poll(5_000));
        assertEquals(4, resequencer.getLastDelivered());
    }

    @Test
    void testTimeoutAfterRestart() throws Exception {
        SequenceBuffer<Integer> out = new SequenceBuffer<>();
        ResequencerEngine<Integer> engine = new ResequencerEngine<>(new IntegerComparator());
        engine.setSequenceSender(out);
        // long enough that the timeout of 4 cannot expire before the stop, even with a pause of the test
        engine.setTimeout(2000);
        engine.start();
        try {
            engine.setLastDelivered(2);
            // 3 is missing, so 4 waits for its timeout
            engine.insert(4);
            engine.stop();
            engine.start();
            engine.insert(5);

            // the timeout of 4 must still expire after the restart
            await().atMost(10, TimeUnit.SECONDS).until(engine::deliverNext);
            engine.deliver();
            assertEquals(4, out.poll(0));
            assertEquals(5, out.poll(0));
        } finally {
            engine.stop();
        }
    }

    @Test
    void testWaitUntilReleasedOnStop() throws Exception {
        ResequencerEngine<Integer> engine = new ResequencerEngine<>(new IntegerComparator());
        engine.setSequenceSender(new SequenceBuffer<>());
        engine.start();
        engine.insert(4);

        // a caller waiting for free capacity
        FutureTask<Void> waiter = new FutureTask<>(() -> {
            engine.waitUntil(s -> s.size() < 1);
            return null;
        });
        Thread thread = new Thread(waiter, "waiter");
        thread.setDaemon(true);
        thread.start();
        await().atMost(5, TimeUnit.SECONDS).until(() -> thread.getState() == Thread.State.WAITING);

        engine.stop();
        try {
            waiter.get(5, TimeUnit.SECONDS);
            fail("waitUntil should fail when the resequencer is stopped");
        } catch (ExecutionException e) {
            assertInstanceOf(RejectedExecutionException.class, e.getCause());
        } catch (TimeoutException e) {
            thread.interrupt();
            fail("waitUntil is still blocked after the resequencer was stopped");
        }
    }

    @Test
    void testStopDoesNotWaitForTheReadyBacklog() throws Exception {
        CountDownLatch sendingFirst = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch releaseOthers = new CountDownLatch(1);
        List<Integer> sent = new CopyOnWriteArrayList<>();
        ResequencerEngine<Integer> engine = new ResequencerEngine<>(new IntegerComparator());
        // a slow downstream processor: the first element is held until released, the others until the end of the test
        engine.setSequenceSender(o -> {
            sent.add(o);
            if (o == 1) {
                sendingFirst.countDown();
                releaseFirst.await(10, TimeUnit.SECONDS);
            } else {
                releaseOthers.await(10, TimeUnit.SECONDS);
            }
        });
        engine.start();
        engine.setLastDelivered(0);
        for (int i = 1; i <= 5; i++) {
            engine.insert(i);
        }

        FutureTask<Void> delivery = new FutureTask<>(() -> {
            engine.deliver();
            return null;
        });
        Thread deliveryThread = new Thread(delivery, "delivery");
        deliveryThread.setDaemon(true);
        FutureTask<Void> stop = new FutureTask<>(() -> {
            engine.stop();
            return null;
        });
        Thread stopThread = new Thread(stop, "stop");
        stopThread.setDaemon(true);
        try {
            deliveryThread.start();
            assertTrue(sendingFirst.await(5, TimeUnit.SECONDS), "the first element was not delivered");

            // the route stops while the downstream processor is busy with the first element
            stopThread.start();
            // stop waits for the element being sent
            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> stop.isDone() || stopThread.getState() == Thread.State.WAITING);
            releaseFirst.countDown();

            // stop returns after the element being sent, without sending the other ready elements
            try {
                stop.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                fail("stop waits for the downstream processing of all ready elements");
            }
            delivery.get(5, TimeUnit.SECONDS);
            assertEquals(List.of(1), sent);
            assertEquals(4, engine.size());
        } finally {
            releaseFirst.countDown();
            releaseOthers.countDown();
        }
    }

    @Test
    void testInsertRejectedWhenStopped() throws Exception {
        ResequencerEngine<Integer> engine = new ResequencerEngine<>(new IntegerComparator());
        engine.setSequenceSender(new SequenceBuffer<>());
        engine.start();
        engine.insert(4);
        engine.stop();

        assertThrows(RejectedExecutionException.class, () -> engine.insert(5));
        // not queued, so it is not delivered after a restart
        assertEquals(1, engine.size());
    }

    @Test
    void testStopWithoutStart() {
        // BaseService.start() calls stop() when the start fails before the engine was started
        ResequencerEngine<Integer> engine = new ResequencerEngine<>(new IntegerComparator());
        assertDoesNotThrow(engine::stop);
    }

    @DisabledIf(value = "isIgnoreLoadTests",
                disabledReason = "Enabled only when the System property 'ignore.load.tests' is not set to 'true'")
    @Test
    void testRandom() throws Exception {
        int input = 1000;
        initResequencer(1000);
        List<Integer> list = new LinkedList<>();
        for (int i = 0; i < input; i++) {
            list.add(i);
        }
        Random random = new Random(System.currentTimeMillis());
        StringBuilder sb = new StringBuilder(4000);
        sb.append("Input sequence: ");
        StopWatch watch = new StopWatch();
        for (int i = input; i > 0; i--) {
            int r = random.nextInt(i);
            int next = list.remove(r);
            sb.append(next).append(" ");
            resequencer.insert(next);
        }
        log.info(sb.toString());

        // clear
        sb.delete(0, sb.length());

        sb.append("Output sequence: ");
        for (int i = 0; i < input; i++) {
            sb.append(buffer.take()).append(" ");
        }
        log.info(sb.toString());
        log.info("Duration = {} ms", watch.taken());
    }

    @DisabledIf(value = "isIgnoreLoadTests",
                disabledReason = "Enabled only when the System property 'ignore.load.tests' is not set to 'true'")
    @Test
    void testReverse() throws Exception {
        initResequencer(1);
        for (int i = 99; i >= 0; i--) {
            resequencer.insert(i);
        }
        StringBuilder sb = new StringBuilder(2500);
        sb.append("Output sequence: ");
        for (int i = 0; i < 100; i++) {
            sb.append(buffer.take()).append(" ");
        }
        log.info(sb.toString());
    }

    private void initResequencer(long timeout) {
        ResequencerEngine<Integer> engine;
        buffer = new SequenceBuffer<>();
        engine = new ResequencerEngine<>(new IntegerComparator());
        engine.setSequenceSender(buffer);
        engine.setTimeout(timeout);
        engine.start();
        resequencer = new ResequencerEngineSync<>(engine);
        runner = new ResequencerRunner<>(resequencer, 50);
        runner.start();

        // wait for runner to run
        await().atMost(3, TimeUnit.SECONDS).until(runner::isRunning);
    }

    boolean isIgnoreLoadTests() {
        return IGNORE_LOAD_TESTS;
    }
}
