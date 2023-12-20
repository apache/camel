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
import java.util.concurrent.TimeUnit;

import org.apache.camel.TestSupport;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResequencerEngineTest extends TestSupport {

    private static final boolean IGNORE_LOAD_TESTS = Boolean.parseBoolean(System.getProperty("ignore.load.tests", "true"));

    private ResequencerEngineSync<Integer> resequencer;
    private ResequencerRunner<Integer> runner;
    private SequenceBuffer<Integer> buffer;

    @Override
    @AfterEach
    public void tearDown() throws Exception {
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
