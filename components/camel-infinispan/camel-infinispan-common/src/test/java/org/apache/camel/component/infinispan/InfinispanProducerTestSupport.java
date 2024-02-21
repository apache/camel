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
package org.apache.camel.component.infinispan;

import java.security.SecureRandom;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.util.CollectionHelper;
import org.awaitility.Awaitility;
import org.infinispan.commons.api.BasicCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public interface InfinispanProducerTestSupport {
    String KEY_ONE = "keyOne";
    String VALUE_ONE = "valueOne";
    String KEY_TWO = "keyTwo";
    String VALUE_TWO = "valueTwo";
    String COMMAND_VALUE = "commandValue";
    String COMMAND_KEY = "commandKey1";
    long LIFESPAN_TIME = 300;
    long LIFESPAN_FOR_MAX_IDLE = -1;
    long MAX_IDLE_TIME = 500;

    static void wait(long timout, Callable<Boolean> condition) {
        Awaitility.await()
                // wait at most the given timeout before failing
                .atMost(timout, TimeUnit.MILLISECONDS)
                // the condition to assert
                .until(condition);
    }

    static void wait(long delay, long timout, Callable<Boolean> condition) {
        long jitter = 50 + new SecureRandom().nextInt(50);

        Awaitility.await()
                // wait for the given delay (plus some jitter)
                .pollDelay(delay + jitter, TimeUnit.MILLISECONDS)
                // wait at most the given timeout before failing
                .atMost(timout, TimeUnit.MILLISECONDS)
                // the condition to assert
                .until(condition);
    }

    BasicCache<Object, Object> getCache();

    BasicCache<Object, Object> getCache(String name);

    ProducerTemplate template();

    FluentProducerTemplate fluentTemplate();

    @Test
    default void keyAndValueArePublishedWithDefaultOperation() {
        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_ONE)
                .send();

        Object value = getCache().get(KEY_ONE);
        assertEquals(VALUE_ONE, value.toString());
    }

    @Test
    default void cacheSizeTest() {
        getCache().put(KEY_ONE, VALUE_ONE);
        getCache().put(KEY_TWO, VALUE_TWO);

        Integer cacheSize = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.SIZE)
                .request(Integer.class);

        assertEquals(cacheSize, Integer.valueOf(2));
    }

    @Test
    default void publishKeyAndValueByExplicitlySpecifyingTheOperation() {
        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUT)
                .send();

        assertEquals(VALUE_ONE, getCache().get(KEY_ONE));
    }

    @Test
    default void publishKeyAndValueAsync() throws Exception {
        assertTrue(getCache().isEmpty());

        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTASYNC)
                .request(CompletableFuture.class)
                .get(5, TimeUnit.SECONDS);

        assertEquals(VALUE_ONE, getCache().get(KEY_ONE));
    }

    @Test
    default void publishKeyAndValueAsyncWithLifespan() throws Exception {
        assertTrue(getCache().isEmpty());

        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTASYNC)
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.LIFESPAN_TIME, LIFESPAN_TIME)
                .withHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS)
                .request(CompletableFuture.class)
                .get(5, TimeUnit.SECONDS);

        assertEquals(VALUE_ONE, getCache().get(KEY_ONE));

        wait(LIFESPAN_TIME, 5000, () -> !getCache().containsKey(KEY_ONE));
    }

    @Test
    default void publishKeyAndValueAsyncWithLifespanAndMaxIdle() throws Exception {
        assertTrue(getCache().isEmpty());

        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTASYNC)
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.LIFESPAN_TIME, LIFESPAN_FOR_MAX_IDLE)
                .withHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS)
                .withHeader(InfinispanConstants.MAX_IDLE_TIME, MAX_IDLE_TIME)
                .withHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.MILLISECONDS)
                .request(CompletableFuture.class)
                .get(5, TimeUnit.SECONDS);

        assertEquals(VALUE_ONE, getCache().get(KEY_ONE));

        wait(MAX_IDLE_TIME, 5000, () -> !getCache().containsKey(KEY_ONE));
    }

    @Test
    default void publishMapNormal() {
        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.MAP, CollectionHelper.mapOf(KEY_ONE, VALUE_ONE, KEY_TWO, VALUE_TWO))
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTALL)
                .send();

        assertEquals(2, getCache().size());
        assertEquals(VALUE_ONE, getCache().get(KEY_ONE));
        assertEquals(VALUE_TWO, getCache().get(KEY_TWO));
    }

    @Test
    default void publishMapWithLifespan() {
        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.MAP, CollectionHelper.mapOf(KEY_ONE, VALUE_ONE, KEY_TWO, VALUE_TWO))
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTALL)
                .withHeader(InfinispanConstants.LIFESPAN_TIME, LIFESPAN_TIME)
                .withHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS)
                .send();

        assertEquals(VALUE_ONE, getCache().get(KEY_ONE));
        assertEquals(VALUE_TWO, getCache().get(KEY_TWO));

        wait(LIFESPAN_TIME, 5000, () -> !getCache().containsKey(KEY_ONE) && !getCache().containsKey(KEY_TWO));
    }

    @Test
    default void publishMapWithLifespanAndMaxIdleTime() {
        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.MAP, CollectionHelper.mapOf(KEY_ONE, VALUE_ONE, KEY_TWO, VALUE_TWO))
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTALL)
                .withHeader(InfinispanConstants.LIFESPAN_TIME, LIFESPAN_FOR_MAX_IDLE)
                .withHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS)
                .withHeader(InfinispanConstants.MAX_IDLE_TIME, MAX_IDLE_TIME)
                .withHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.MILLISECONDS)
                .send();

        assertEquals(VALUE_ONE, getCache().get(KEY_ONE));
        assertEquals(VALUE_TWO, getCache().get(KEY_TWO));

        wait(MAX_IDLE_TIME, 5000, () -> !getCache().containsKey(KEY_ONE) && !getCache().containsKey(KEY_TWO));
    }

    @Test
    default void publishMapNormalAsync() throws Exception {
        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.MAP, CollectionHelper.mapOf(KEY_ONE, VALUE_ONE, KEY_TWO, VALUE_TWO))
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTALLASYNC)
                .request(CompletableFuture.class)
                .get(5, TimeUnit.SECONDS);

        assertEquals(VALUE_ONE, getCache().get(KEY_ONE));
        assertEquals(VALUE_TWO, getCache().get(KEY_TWO));
    }

    @Test
    default void publishMapWithLifespanAsync() throws Exception {
        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.MAP, CollectionHelper.mapOf(KEY_ONE, VALUE_ONE, KEY_TWO, VALUE_TWO))
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTALLASYNC)
                .withHeader(InfinispanConstants.LIFESPAN_TIME, LIFESPAN_TIME)
                .withHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS)
                .request(CompletableFuture.class)
                .get(5, TimeUnit.SECONDS);

        assertEquals(VALUE_ONE, getCache().get(KEY_ONE));
        assertEquals(VALUE_TWO, getCache().get(KEY_TWO));

        wait(LIFESPAN_TIME, 5000, () -> !getCache().containsKey(KEY_ONE) && !getCache().containsKey(KEY_TWO));
    }

    @Test
    default void publishMapWithLifespanAndMaxIdleTimeAsync() throws Exception {
        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.MAP, CollectionHelper.mapOf(KEY_ONE, VALUE_ONE, KEY_TWO, VALUE_TWO))
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTALLASYNC)
                .withHeader(InfinispanConstants.LIFESPAN_TIME, LIFESPAN_FOR_MAX_IDLE)
                .withHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS)
                .withHeader(InfinispanConstants.MAX_IDLE_TIME, MAX_IDLE_TIME)
                .withHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.MILLISECONDS)
                .request(CompletableFuture.class)
                .get(5, TimeUnit.SECONDS);

        assertEquals(VALUE_ONE, getCache().get(KEY_ONE));
        assertEquals(VALUE_TWO, getCache().get(KEY_TWO));

        wait(MAX_IDLE_TIME, 5000, () -> !getCache().containsKey(KEY_ONE) && !getCache().containsKey(KEY_TWO));
    }

    @Test
    default void putIfAbsentAlreadyExists() {
        getCache().put(KEY_ONE, VALUE_ONE);

        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTIFABSENT)
                .send();

        assertEquals(VALUE_ONE, getCache().get(KEY_ONE));
        assertEquals(1, getCache().size());
    }

    @Test
    default void putIfAbsentNotExists() {
        getCache().put(KEY_ONE, VALUE_ONE);

        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_TWO)
                .withHeader(InfinispanConstants.VALUE, VALUE_TWO)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTIFABSENT)
                .send();

        assertEquals(VALUE_TWO, getCache().get(KEY_TWO));
        assertEquals(2, getCache().size());
    }

    @Test
    default void putIfAbsentKeyAndValueAsync() throws Exception {
        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTIFABSENTASYNC)
                .request(CompletableFuture.class)
                .get(1, TimeUnit.SECONDS);

        assertEquals(VALUE_ONE, getCache().get(KEY_ONE));
    }

    @Test
    default void putIfAbsentKeyAndValueAsyncWithLifespan() throws Exception {
        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTIFABSENTASYNC)
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.LIFESPAN_TIME, LIFESPAN_TIME)
                .withHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS)
                .request(CompletableFuture.class)
                .get(5, TimeUnit.SECONDS);

        assertEquals(VALUE_ONE, getCache().get(KEY_ONE));

        wait(LIFESPAN_TIME, 5000, () -> !getCache().containsKey(KEY_ONE));
    }

    @Test
    default void putIfAbsentKeyAndValueAsyncWithLifespanAndMaxIdle() throws Exception {
        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTIFABSENTASYNC)
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.LIFESPAN_TIME, LIFESPAN_FOR_MAX_IDLE)
                .withHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS)
                .withHeader(InfinispanConstants.MAX_IDLE_TIME, MAX_IDLE_TIME)
                .withHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.MILLISECONDS)
                .request(CompletableFuture.class)
                .get(5, TimeUnit.SECONDS);

        assertEquals(VALUE_ONE, getCache().get(KEY_ONE));
        Thread.sleep(MAX_IDLE_TIME * 2);
        assertFalse(getCache().containsKey(KEY_ONE));
    }

    @Test
    default void notContainsKeyTest() {
        getCache().put(KEY_ONE, VALUE_ONE);

        boolean result = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_TWO)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.CONTAINSKEY)
                .request(Boolean.class);

        assertFalse(result);
    }

    @Test
    default void containsKeyTest() {
        getCache().put(KEY_ONE, VALUE_ONE);

        boolean result = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.CONTAINSKEY)
                .request(Boolean.class);

        assertTrue(result);
    }

    @Test
    default void notContainsValueTest() {
        getCache().put(KEY_ONE, VALUE_ONE);

        boolean result = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.VALUE, VALUE_TWO)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.CONTAINSVALUE)
                .request(Boolean.class);

        assertFalse(result);
    }

    @Test
    default void containsValueTest() {
        getCache().put(KEY_ONE, VALUE_ONE);

        boolean result = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.CONTAINSVALUE)
                .request(Boolean.class);

        assertTrue(result);
    }

    @Test
    default void publishKeyAndValueWithLifespan() {
        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.LIFESPAN_TIME, LIFESPAN_TIME)
                .withHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUT)
                .send();

        assertEquals(VALUE_ONE, getCache().get(KEY_ONE).toString());
        wait(LIFESPAN_TIME, 5000, () -> !getCache().containsKey(KEY_ONE));
    }

    @Test
    default void getOrDefault() {
        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUT)
                .send();

        assertEquals(VALUE_ONE, getCache().get(KEY_ONE));

        String result1 = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.DEFAULT_VALUE, "defaultTest")
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.GETORDEFAULT)
                .request(String.class);

        assertEquals(VALUE_ONE, result1);

        String result2 = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_TWO)
                .withHeader(InfinispanConstants.DEFAULT_VALUE, "defaultTest")
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.GETORDEFAULT)
                .request(String.class);

        assertEquals("defaultTest", result2);
    }

    @Test
    default void putOperationReturnsThePreviousValue() {
        getCache().put(KEY_ONE, "existing value");

        String result = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUT)
                .request(String.class);

        assertEquals("existing value", result);
    }

    @Test
    default void computeOperation() {
        getCache().put(KEY_ONE, "existing value");

        String result = fluentTemplate()
                .to("direct:compute")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.COMPUTE)
                .request(String.class);

        assertEquals("existing valuereplay", result);
    }

    @Test
    default void computeAsyncOperation() throws Exception {
        getCache().put(KEY_ONE, "existing value");

        CompletableFuture<?> result = fluentTemplate()
                .to("direct:compute")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.COMPUTEASYNC)
                .request(CompletableFuture.class);

        assertEquals("existing valuereplay", result.get());
    }

    @Test
    default void retrievesAValueByKey() {
        getCache().put(KEY_ONE, VALUE_ONE);

        String result = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.GET)
                .request(String.class);

        assertEquals(VALUE_ONE, result);
    }

    @Test
    default void replaceAValueByKey() {
        getCache().put(KEY_ONE, VALUE_ONE);

        String result = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_TWO)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.REPLACE)
                .request(String.class);

        assertEquals(VALUE_ONE, result);
        assertEquals(VALUE_TWO, getCache().get(KEY_ONE));
    }

    @Test
    default void replaceAValueByKeyWithLifespan() {
        getCache().put(KEY_ONE, VALUE_ONE);

        Object result = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_TWO)
                .withHeader(InfinispanConstants.LIFESPAN_TIME, LIFESPAN_TIME)
                .withHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.REPLACE)
                .request(String.class);

        assertEquals(VALUE_ONE, result);
        assertEquals(VALUE_TWO, getCache().get(KEY_ONE));

        wait(LIFESPAN_TIME, 5000, () -> !getCache().containsKey(KEY_ONE));
    }

    @Test
    default void replaceAValueByKeyWithLifespanAndMaxIdleTime() {
        getCache().put(KEY_ONE, VALUE_ONE);

        Object result = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_TWO)
                .withHeader(InfinispanConstants.LIFESPAN_TIME, LIFESPAN_FOR_MAX_IDLE)
                .withHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS)
                .withHeader(InfinispanConstants.MAX_IDLE_TIME, MAX_IDLE_TIME)
                .withHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.MILLISECONDS)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.REPLACE)
                .request(String.class);

        assertEquals(VALUE_ONE, result);
        assertEquals(VALUE_TWO, getCache().get(KEY_ONE));

        wait(MAX_IDLE_TIME, 5000, () -> !getCache().containsKey(KEY_ONE));
    }

    @Test
    default void replaceAValueByKeyWithOldValue() {
        getCache().put(KEY_ONE, VALUE_ONE);

        Boolean result = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_TWO)
                .withHeader(InfinispanConstants.OLD_VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.REPLACE)
                .request(Boolean.class);

        assertTrue(result);
        assertEquals(VALUE_TWO, getCache().get(KEY_ONE));
    }

    @Test
    default void replaceAValueByKeyWithLifespanWithOldValue() {
        getCache().put(KEY_ONE, VALUE_ONE);

        Object result = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_TWO)
                .withHeader(InfinispanConstants.OLD_VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.LIFESPAN_TIME, LIFESPAN_TIME)
                .withHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.REPLACE)
                .request(Boolean.class);

        assertEquals(Boolean.TRUE, result);
        assertEquals(VALUE_TWO, getCache().get(KEY_ONE));

        wait(LIFESPAN_TIME, 5000, () -> !getCache().containsKey(KEY_ONE));
    }

    @Test
    default void replaceAValueByKeyWithLifespanAndMaxIdleTimeWithOldValue() {
        getCache().put(KEY_ONE, VALUE_ONE);

        Object result = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_TWO)
                .withHeader(InfinispanConstants.OLD_VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.LIFESPAN_TIME, LIFESPAN_FOR_MAX_IDLE)
                .withHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS)
                .withHeader(InfinispanConstants.MAX_IDLE_TIME, MAX_IDLE_TIME)
                .withHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.MILLISECONDS)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.REPLACE)
                .request(Boolean.class);

        assertEquals(Boolean.TRUE, result);
        assertEquals(VALUE_TWO, getCache().get(KEY_ONE));

        wait(MAX_IDLE_TIME, 5000, () -> !getCache().containsKey(KEY_ONE));
    }

    @Test
    default void replaceAValueByKeyAsync() {
        getCache().put(KEY_ONE, VALUE_ONE);

        String result = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_TWO)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.REPLACEASYNC)
                .request(String.class);

        assertEquals(VALUE_ONE, result);
        assertEquals(VALUE_TWO, getCache().get(KEY_ONE));
    }

    @Test
    default void replaceAValueByKeyWithLifespanAsync() throws Exception {
        getCache().put(KEY_ONE, VALUE_ONE);

        Object result = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_TWO)
                .withHeader(InfinispanConstants.LIFESPAN_TIME, LIFESPAN_TIME)
                .withHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.REPLACEASYNC)
                .request(CompletableFuture.class)
                .get(5, TimeUnit.SECONDS);

        assertEquals(VALUE_ONE, result);
        assertEquals(VALUE_TWO, getCache().get(KEY_ONE));

        wait(LIFESPAN_TIME, 5000, () -> !getCache().containsKey(KEY_ONE));
    }

    @Test
    default void replaceAValueByKeyWithLifespanAndMaxIdleTimeAsync() throws Exception {
        getCache().put(KEY_ONE, VALUE_ONE);

        Object result = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_TWO)
                .withHeader(InfinispanConstants.LIFESPAN_TIME, LIFESPAN_FOR_MAX_IDLE)
                .withHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS)
                .withHeader(InfinispanConstants.MAX_IDLE_TIME, MAX_IDLE_TIME)
                .withHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.MILLISECONDS)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.REPLACEASYNC)
                .request(CompletableFuture.class)
                .get(5, TimeUnit.SECONDS);

        assertEquals(VALUE_ONE, result);
        assertEquals(VALUE_TWO, getCache().get(KEY_ONE));

        wait(MAX_IDLE_TIME, 5000, () -> !getCache().containsKey(KEY_ONE));
    }

    @Test
    default void replaceAValueByKeyAsyncWithOldValue() throws ExecutionException, InterruptedException {
        getCache().put(KEY_ONE, VALUE_ONE);

        CompletableFuture<Boolean> result = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_TWO)
                .withHeader(InfinispanConstants.OLD_VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.REPLACEASYNC)
                .request(CompletableFuture.class);

        assertEquals(Boolean.TRUE, result.get());
        assertEquals(VALUE_TWO, getCache().get(KEY_ONE));
    }

    @Test
    default void replaceAValueByKeyWithLifespanAsyncWithOldValue() throws Exception {
        getCache().put(KEY_ONE, VALUE_ONE);

        Object result = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_TWO)
                .withHeader(InfinispanConstants.OLD_VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.LIFESPAN_TIME, LIFESPAN_TIME)
                .withHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.REPLACEASYNC)
                .request(CompletableFuture.class)
                .get(5, TimeUnit.SECONDS);

        assertEquals(Boolean.TRUE, result);
        assertEquals(VALUE_TWO, getCache().get(KEY_ONE));

        wait(MAX_IDLE_TIME, 5000, () -> !getCache().containsKey(KEY_ONE));
    }

    @Test
    default void replaceAValueByKeyWithLifespanAndMaxIdleTimeAsyncWithOldValue() throws Exception {
        getCache().put(KEY_ONE, VALUE_ONE);

        Object result = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_TWO)
                .withHeader(InfinispanConstants.OLD_VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.LIFESPAN_TIME, LIFESPAN_FOR_MAX_IDLE)
                .withHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS)
                .withHeader(InfinispanConstants.MAX_IDLE_TIME, MAX_IDLE_TIME)
                .withHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.MILLISECONDS)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.REPLACEASYNC)
                .request(CompletableFuture.class)
                .get(5, TimeUnit.SECONDS);

        assertEquals(Boolean.TRUE, result);
        assertEquals(VALUE_TWO, getCache().get(KEY_ONE));

        wait(MAX_IDLE_TIME, 5000, () -> !getCache().containsKey(KEY_ONE));
    }

    @Test
    default void deletesExistingValueByKey() {
        getCache().put(KEY_ONE, VALUE_ONE);

        String result = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.REMOVE)
                .request(String.class);

        assertEquals(VALUE_ONE, result);
        assertNull(getCache().get(KEY_ONE));
    }

    @Test
    default void deletesExistingValueByKeyAsync() throws Exception {
        getCache().put(KEY_ONE, VALUE_ONE);

        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.REMOVEASYNC)
                .request(CompletableFuture.class)
                .get(5, TimeUnit.SECONDS);

        assertNull(getCache().get(KEY_ONE));
    }

    @Test
    default void deletesExistingValueByKeyWithValue() {
        getCache().put(KEY_ONE, VALUE_ONE);

        Boolean result = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.REMOVE)
                .request(Boolean.class);

        assertTrue(result);
        assertNull(getCache().get(KEY_ONE));
    }

    @Test
    default void deletesExistingValueByKeyAsyncWithValue() throws Exception {
        getCache().put(KEY_ONE, VALUE_ONE);

        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.REMOVEASYNC)
                .request(CompletableFuture.class)
                .get(5, TimeUnit.SECONDS);

        assertNull(getCache().get(KEY_ONE));
    }

    @Test
    default void clearsAllValues() {
        getCache().put(KEY_ONE, VALUE_ONE);

        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.CLEAR)
                .send();

        assertTrue(getCache().isEmpty());
    }

    @Test
    default void testUriCommandOption() {
        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, COMMAND_KEY)
                .withHeader(InfinispanConstants.VALUE, COMMAND_VALUE)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUT)
                .request(String.class);

        assertEquals(COMMAND_VALUE, getCache().get(COMMAND_KEY));

        String result2 = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, COMMAND_KEY)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.GET)
                .request(String.class);

        assertEquals(COMMAND_VALUE, result2);

        String result3 = fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, COMMAND_KEY)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.REMOVE)
                .request(String.class);

        assertEquals(COMMAND_VALUE, result3);

        assertNull(getCache().get(COMMAND_KEY));
        assertTrue(getCache().isEmpty());

        getCache().put(COMMAND_KEY, COMMAND_VALUE);
        getCache().put("keyTest", "valueTest");

        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.CLEAR)
                .send();

        assertTrue(getCache().isEmpty());
    }

    @Test
    default void clearAsyncTest() throws Exception {
        getCache().put(KEY_ONE, VALUE_ONE);
        getCache().put(KEY_TWO, VALUE_TWO);

        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.CLEARASYNC)
                .request(CompletableFuture.class)
                .get(5, TimeUnit.SECONDS);

        assertTrue(getCache().isEmpty());
    }

    @Test
    default void publishKeyAndValueByExplicitlySpecifyingTheKeyAndValueOptions() {
        fluentTemplate()
                .to("direct:explicitput")
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUT)
                .send();

        assertEquals("3", getCache().get("a"));
    }

    @Test
    default void publishKeyAndValueByExplicitlySpecifyingTheKeyAndValueOptionsHeaderHavePriorities() {
        fluentTemplate()
                .to("direct:explicitput")
                .withHeader(InfinispanConstants.KEY, KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, VALUE_ONE)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUT)
                .send();

        assertEquals(VALUE_ONE, getCache().get(KEY_ONE));
    }
}
