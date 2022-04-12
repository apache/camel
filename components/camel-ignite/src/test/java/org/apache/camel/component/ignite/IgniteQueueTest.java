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
package org.apache.camel.component.ignite;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.camel.component.ignite.queue.IgniteQueueComponent;
import org.apache.camel.component.ignite.queue.IgniteQueueEndpoint;
import org.apache.camel.component.ignite.queue.IgniteQueueOperation;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.CollectionConfiguration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IgniteQueueTest extends AbstractIgniteTest {

    @Override
    protected String getScheme() {
        return "ignite-queue";
    }

    @Override
    protected AbstractIgniteComponent createComponent() {
        return IgniteQueueComponent.fromConfiguration(createConfiguration());
    }

    @Test
    public void testOperations() {
        boolean result = template.requestBody("ignite-queue:" + resourceUid + "?operation=ADD", "hello", boolean.class);
        Assertions.assertThat(result).isTrue();
        Assertions.assertThat(ignite().queue(resourceUid, 0, new CollectionConfiguration()).contains("hello")).isTrue();

        result = template.requestBody("ignite-queue:" + resourceUid + "?operation=CONTAINS", "hello", boolean.class);
        Assertions.assertThat(result).isTrue();
        Assertions.assertThat(ignite().queue(resourceUid, 0, new CollectionConfiguration()).contains("hello")).isTrue();

        result = template.requestBody("ignite-queue:" + resourceUid + "?operation=REMOVE", "hello", boolean.class);
        Assertions.assertThat(result).isTrue();
        Assertions.assertThat(ignite().queue(resourceUid, 0, new CollectionConfiguration()).contains("hello")).isFalse();

        result = template.requestBody("ignite-queue:" + resourceUid + "?operation=CONTAINS", "hello", boolean.class);
        Assertions.assertThat(result).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testOperations2() {
        for (int i = 0; i < 100; i++) {
            template.requestBody("ignite-queue:" + resourceUid + "?operation=ADD", "hello" + i);
        }

        // SIZE
        int size = template.requestBody("ignite-queue:" + resourceUid + "?operation=SIZE", "hello", int.class);
        Assertions.assertThat(size).isEqualTo(100);
        Assertions.assertThat(ignite().queue(resourceUid, 0, new CollectionConfiguration()).size()).isEqualTo(100);

        List<String> toRetain = Lists.newArrayList();
        for (int i = 0; i < 50; i++) {
            toRetain.add("hello" + i);
        }

        // RETAIN_ALL
        boolean retained = template.requestBodyAndHeader("ignite-queue:" + resourceUid + "?operation=CLEAR", toRetain,
                IgniteConstants.IGNITE_QUEUE_OPERATION,
                IgniteQueueOperation.RETAIN_ALL, boolean.class);
        Assertions.assertThat(retained).isTrue();

        // SIZE
        size = template.requestBody("ignite-queue:" + resourceUid + "?operation=SIZE", "hello", int.class);
        Assertions.assertThat(size).isEqualTo(50);
        Assertions.assertThat(ignite().queue(resourceUid, 0, new CollectionConfiguration()).size()).isEqualTo(50);

        // ITERATOR
        Iterator<String> iterator
                = template.requestBody("ignite-queue:" + resourceUid + "?operation=ITERATOR", "hello", Iterator.class);
        Assertions.assertThat(Iterators.toArray(iterator, String.class)).containsExactlyElementsOf(toRetain);

        // ARRAY
        String[] array = template.requestBody("ignite-queue:" + resourceUid + "?operation=ARRAY", "hello", String[].class);
        Assertions.assertThat(array).containsExactlyElementsOf(toRetain);

        // CLEAR
        Object result = template.requestBody("ignite-queue:" + resourceUid + "?operation=CLEAR", "hello", String.class);
        Assertions.assertThat(result).isEqualTo("hello");
        Assertions.assertThat(ignite().queue(resourceUid, 0, new CollectionConfiguration()).size()).isZero();

        // SIZE
        size = template.requestBody("ignite-queue:" + resourceUid + "?operation=SIZE", "hello", int.class);
        Assertions.assertThat(size).isZero();
        Assertions.assertThat(ignite().queue(resourceUid, 0, new CollectionConfiguration()).size()).isZero();
    }

    @Test
    public void testRetainSingle() {
        // Fill data.
        for (int i = 0; i < 100; i++) {
            template.requestBody("ignite-queue:" + resourceUid + "?operation=ADD", "hello" + i);
        }

        boolean retained
                = template.requestBody("ignite-queue:" + resourceUid + "?operation=RETAIN_ALL", "hello10", boolean.class);
        Assertions.assertThat(retained).isTrue();

        // ARRAY
        String[] array = template.requestBody("ignite-queue:" + resourceUid + "?operation=ARRAY", "hello", String[].class);
        Assertions.assertThat(array).containsExactly("hello10");
    }

    @Test
    public void testCollectionsAsCacheObject() {
        // Fill data.
        for (int i = 0; i < 100; i++) {
            template.requestBody("ignite-queue:" + resourceUid + "?operation=ADD", "hello" + i);
        }

        // Add the set.
        Set<String> toAdd = Sets.newHashSet("hello101", "hello102", "hello103");
        template.requestBody("ignite-queue:" + resourceUid + "?operation=ADD&treatCollectionsAsCacheObjects=true", toAdd);

        // Size must be 101, not 103.
        int size = template.requestBody("ignite-queue:" + resourceUid + "?operation=SIZE", "hello", int.class);
        Assertions.assertThat(size).isEqualTo(101);
        Assertions.assertThat(ignite().queue(resourceUid, 0, new CollectionConfiguration()).size()).isEqualTo(101);
        Assertions.assertThat(ignite().queue(resourceUid, 0, new CollectionConfiguration()).contains(toAdd)).isTrue();

        // Check whether the Set contains the Set.
        boolean contains = template.requestBody(
                "ignite-queue:" + resourceUid + "?operation=CONTAINS&treatCollectionsAsCacheObjects=true", toAdd,
                boolean.class);
        Assertions.assertThat(contains).isTrue();

        // Delete the Set.
        template.requestBody("ignite-queue:" + resourceUid + "?operation=REMOVE&treatCollectionsAsCacheObjects=true", toAdd);

        // Size must be 100 again.
        size = template.requestBody("ignite-queue:" + resourceUid + "?operation=SIZE", "hello", int.class);
        Assertions.assertThat(size).isEqualTo(100);
        Assertions.assertThat(ignite().queue(resourceUid, 0, new CollectionConfiguration()).size()).isEqualTo(100);
        Assertions.assertThat(ignite().queue(resourceUid, 0, new CollectionConfiguration()).contains(toAdd)).isFalse();

    }

    @Test
    public void testWithConfiguration() {
        CollectionConfiguration configuration = new CollectionConfiguration();
        configuration.setCacheMode(CacheMode.PARTITIONED);

        context.getRegistry().bind("config", configuration);

        IgniteQueueEndpoint igniteEndpoint = context
                .getEndpoint("ignite-queue:" + resourceUid + "?operation=ADD&configuration=#config", IgniteQueueEndpoint.class);
        template.requestBody(igniteEndpoint, "hello");

        Assertions.assertThat(ignite().queue(resourceUid, 0, configuration).size()).isEqualTo(1);
        Assertions.assertThat(igniteEndpoint.getConfiguration()).isEqualTo(configuration);
    }

    @Test
    public void testBoundedQueueAndOtherOperations() throws Exception {
        List<String> list = Lists.newArrayList();

        // Fill data.
        for (int i = 0; i < 100; i++) {
            template.requestBody("ignite-queue:" + resourceUid + "?operation=ADD&capacity=100", "hello" + i);
            list.add("hello" + i);
        }

        // NOTE: Unfortunately the behaviour of IgniteQueue doesn't adhere to
        // the overridden ADD method. It should return an Exception.
        Assertions.assertThat(
                template.requestBody("ignite-queue:" + resourceUid + "?operation=ADD&capacity=100", "hello101", boolean.class))
                .isFalse();
        Assertions.assertThat(template.requestBody("ignite-queue:" + resourceUid + "?operation=OFFER&capacity=100", "hello101",
                boolean.class)).isFalse();

        final CountDownLatch latch = new CountDownLatch(1);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                Assertions.assertThat(template.requestBody("ignite-queue:" + resourceUid + "?operation=PUT&capacity=100",
                        "hello101", boolean.class)).isFalse();
                latch.countDown();
            }
        });

        t.start();

        // Wait 2 seconds and check that the thread was blocked.
        Assertions.assertThat(latch.await(2000, TimeUnit.MILLISECONDS)).isFalse();
        t.interrupt();

        // PEEK and ELEMENT.
        Assertions.assertThat(
                template.requestBody("ignite-queue:" + resourceUid + "?operation=PEEK&capacity=100", null, String.class))
                .isEqualTo("hello0");
        Assertions.assertThat(
                template.requestBody("ignite-queue:" + resourceUid + "?operation=ELEMENT&capacity=100", null, String.class))
                .isEqualTo("hello0");

        // TAKE.
        Assertions.assertThat(
                template.requestBody("ignite-queue:" + resourceUid + "?operation=TAKE&capacity=100", null, String.class))
                .isEqualTo("hello0");
        Assertions
                .assertThat(
                        template.requestBody("ignite-queue:" + resourceUid + "?operation=SIZE&capacity=100", null, int.class))
                .isEqualTo(99);

        // Now drain.
        Assertions.assertThat(
                template.requestBody("ignite-queue:" + resourceUid + "?operation=DRAIN&capacity=100", null, String[].class))
                .hasSize(99);
        Assertions
                .assertThat(
                        template.requestBody("ignite-queue:" + resourceUid + "?operation=SIZE&capacity=100", null, int.class))
                .isZero();
        Assertions.assertThat(
                template.requestBody("ignite-queue:" + resourceUid + "?operation=POLL&capacity=100", null, String.class))
                .isNull();

        // TAKE.
        t = new Thread(new Runnable() {
            @Override
            public void run() {
                Assertions.assertThat(template.requestBody("ignite-queue:" + resourceUid + "?operation=TAKE&capacity=100", null,
                        String.class)).isEqualTo("hello102");
                latch.countDown();
            }
        });

        t.start();

        // Element was returned.
        Assertions.assertThat(
                template.requestBody("ignite-queue:" + resourceUid + "?operation=ADD&capacity=100", "hello102", boolean.class))
                .isTrue();
        Assertions.assertThat(latch.await(1000, TimeUnit.MILLISECONDS)).isTrue();

        // POLL with a timeout.
        Assertions.assertThat(Executors.newSingleThreadExecutor().submit(new Callable<Long>() {
            @Override
            public Long call() {
                Stopwatch sw = Stopwatch.createStarted();
                Assertions.assertThat(template.requestBody(
                        "ignite-queue:" + resourceUid + "?operation=POLL&timeoutMillis=1000&capacity=100", null, String.class))
                        .isNull();
                return sw.elapsed(TimeUnit.MILLISECONDS);
            }
        }).get()).isGreaterThanOrEqualTo(1000L);

    }

    @AfterEach
    public void deleteQueue() {
        ignite().queue(resourceUid, 0, null).close();
    }

}
