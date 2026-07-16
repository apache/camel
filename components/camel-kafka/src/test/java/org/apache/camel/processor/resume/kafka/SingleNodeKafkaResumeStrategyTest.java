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
package org.apache.camel.processor.resume.kafka;

import java.lang.reflect.Field;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.resume.ResumeAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SingleNodeKafkaResumeStrategy}.
 */
public class SingleNodeKafkaResumeStrategyTest {

    @Test
    void testStopDoesNotThrowWhenLockNotAcquired() throws Exception {
        SingleNodeKafkaResumeStrategy strategy = new SingleNodeKafkaResumeStrategy();

        ReentrantLock lock = getField(strategy, "writeLock");
        // Acquire the lock from another thread so tryLock fails in stop()
        Thread holder = new Thread(lock::lock);
        holder.start();
        holder.join();

        // stop() should not throw IllegalMonitorStateException
        assertDoesNotThrow(strategy::stop);

        // Release the lock from the holder thread
        Thread releaser = new Thread(lock::unlock);
        releaser.start();
        releaser.join();
    }

    @Test
    void testGetAdapterReturnsAdapterWithoutLoadCache() {
        SingleNodeKafkaResumeStrategy strategy = new SingleNodeKafkaResumeStrategy();

        ResumeAdapter adapter = new TestAdapter();
        strategy.setAdapter(adapter);

        assertSame(adapter, strategy.getAdapter());
    }

    @Test
    void testGetAdapterWaitsForInitializationWhenLatchIsSet() throws Exception {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        KafkaResumeStrategyConfiguration config
                = new KafkaResumeStrategyConfigurationBuilder(props, props)
                        .withTopic("test-topic")
                        .build();

        SingleNodeKafkaResumeStrategy strategy = new SingleNodeKafkaResumeStrategy(config);

        ResumeAdapter adapter = new TestAdapter();
        strategy.setAdapter(adapter);

        CountDownLatch latch = new CountDownLatch(1);
        setField(strategy, "initLatch", latch);

        // Count down so waitForInitialization() completes immediately
        latch.countDown();

        assertSame(adapter, strategy.getAdapter());
    }

    static class TestAdapter implements ResumeAdapter {
        @Override
        public void resume() {
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
