/**
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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.apache.camel.component.ignite.queue.IgniteQueueComponent;
import org.apache.camel.component.ignite.queue.IgniteQueueEndpoint;
import org.apache.camel.component.ignite.queue.IgniteQueueOperation;
import org.apache.camel.impl.JndiRegistry;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.CollectionConfiguration;
import org.junit.After;
import org.junit.Test;

import static com.google.common.truth.Truth.assert_;


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
        boolean result = template.requestBody("ignite-queue:abc?operation=ADD", "hello", boolean.class);
        assert_().that(result).isTrue();
        assert_().that(ignite().queue("abc", 0, new CollectionConfiguration()).contains("hello")).isTrue();

        result = template.requestBody("ignite-queue:abc?operation=CONTAINS", "hello", boolean.class);
        assert_().that(result).isTrue();
        assert_().that(ignite().queue("abc", 0, new CollectionConfiguration()).contains("hello")).isTrue();

        result = template.requestBody("ignite-queue:abc?operation=REMOVE", "hello", boolean.class);
        assert_().that(result).isTrue();
        assert_().that(ignite().queue("abc", 0, new CollectionConfiguration()).contains("hello")).isFalse();

        result = template.requestBody("ignite-queue:abc?operation=CONTAINS", "hello", boolean.class);
        assert_().that(result).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testOperations2() {
        for (int i = 0; i < 100; i++) {
            template.requestBody("ignite-queue:abc?operation=ADD", "hello" + i);
        }

        // SIZE
        int size = template.requestBody("ignite-queue:abc?operation=SIZE", "hello", int.class);
        assert_().that(size).isEqualTo(100);
        assert_().that(ignite().queue("abc", 0, new CollectionConfiguration()).size()).isEqualTo(100);

        List<String> toRetain = Lists.newArrayList();
        for (int i = 0; i < 50; i++) {
            toRetain.add("hello" + i);
        }

        // RETAIN_ALL
        boolean retained = template.requestBodyAndHeader("ignite-queue:abc?operation=CLEAR", toRetain, IgniteConstants.IGNITE_QUEUE_OPERATION, IgniteQueueOperation.RETAIN_ALL, boolean.class);
        assert_().that(retained).isTrue();

        // SIZE
        size = template.requestBody("ignite-queue:abc?operation=SIZE", "hello", int.class);
        assert_().that(size).isEqualTo(50);
        assert_().that(ignite().queue("abc", 0, new CollectionConfiguration()).size()).isEqualTo(50);

        // ITERATOR
        Iterator<String> iterator = template.requestBody("ignite-queue:abc?operation=ITERATOR", "hello", Iterator.class);
        assert_().that(Iterators.toArray(iterator, String.class)).asList().containsExactlyElementsIn(toRetain).inOrder();

        // ARRAY
        String[] array = template.requestBody("ignite-queue:abc?operation=ARRAY", "hello", String[].class);
        assert_().that(array).asList().containsExactlyElementsIn(toRetain).inOrder();

        // CLEAR
        Object result = template.requestBody("ignite-queue:abc?operation=CLEAR", "hello", String.class);
        assert_().that(result).isEqualTo("hello");
        assert_().that(ignite().queue("abc", 0, new CollectionConfiguration()).size()).isEqualTo(0);

        // SIZE
        size = template.requestBody("ignite-queue:abc?operation=SIZE", "hello", int.class);
        assert_().that(size).isEqualTo(0);
        assert_().that(ignite().queue("abc", 0, new CollectionConfiguration()).size()).isEqualTo(0);
    }

    @Test
    public void testRetainSingle() {
        // Fill data.
        for (int i = 0; i < 100; i++) {
            template.requestBody("ignite-queue:abc?operation=ADD", "hello" + i);
        }

        boolean retained = template.requestBody("ignite-queue:abc?operation=RETAIN_ALL", "hello10", boolean.class);
        assert_().that(retained).isTrue();

        // ARRAY
        String[] array = template.requestBody("ignite-queue:abc?operation=ARRAY", "hello", String[].class);
        assert_().that(array).asList().containsExactly("hello10");
    }

    @Test
    public void testCollectionsAsCacheObject() {
        // Fill data.
        for (int i = 0; i < 100; i++) {
            template.requestBody("ignite-queue:abc?operation=ADD", "hello" + i);
        }

        // Add the set.
        Set<String> toAdd = Sets.newHashSet("hello101", "hello102", "hello103");
        template.requestBody("ignite-queue:abc?operation=ADD&treatCollectionsAsCacheObjects=true", toAdd);

        // Size must be 101, not 103.
        int size = template.requestBody("ignite-queue:abc?operation=SIZE", "hello", int.class);
        assert_().that(size).isEqualTo(101);
        assert_().that(ignite().queue("abc", 0, new CollectionConfiguration()).size()).isEqualTo(101);
        assert_().that(ignite().queue("abc", 0, new CollectionConfiguration()).contains(toAdd)).isTrue();

        // Check whether the Set contains the Set.
        boolean contains = template.requestBody("ignite-queue:abc?operation=CONTAINS&treatCollectionsAsCacheObjects=true", toAdd, boolean.class);
        assert_().that(contains).isTrue();

        // Delete the Set.
        template.requestBody("ignite-queue:abc?operation=REMOVE&treatCollectionsAsCacheObjects=true", toAdd);

        // Size must be 100 again.
        size = template.requestBody("ignite-queue:abc?operation=SIZE", "hello", int.class);
        assert_().that(size).isEqualTo(100);
        assert_().that(ignite().queue("abc", 0, new CollectionConfiguration()).size()).isEqualTo(100);
        assert_().that(ignite().queue("abc", 0, new CollectionConfiguration()).contains(toAdd)).isFalse();

    }

    @Test
    public void testWithConfiguration() {
        CollectionConfiguration configuration = new CollectionConfiguration();
        configuration.setCacheMode(CacheMode.PARTITIONED);

        context.getRegistry(JndiRegistry.class).bind("config", configuration);

        IgniteQueueEndpoint igniteEndpoint = context.getEndpoint("ignite-queue:abc?operation=ADD&configuration=#config", IgniteQueueEndpoint.class);
        template.requestBody(igniteEndpoint, "hello");

        assert_().that(ignite().queue("abc", 0, configuration).size()).isEqualTo(1);
        assert_().that(igniteEndpoint.getConfiguration()).isEqualTo(configuration);
    }

    @Test
    public void testBoundedQueueAndOtherOperations() throws Exception {
        List<String> list = Lists.newArrayList();

        // Fill data.
        for (int i = 0; i < 100; i++) {
            template.requestBody("ignite-queue:def?operation=ADD&capacity=100", "hello" + i);
            list.add("hello" + i);
        }

        // NOTE: Unfortunately the behaviour of IgniteQueue doesn't adhere to the overridden ADD method. It should return an Exception.
        assert_().that(template.requestBody("ignite-queue:def?operation=ADD&capacity=100", "hello101", boolean.class)).isFalse();
        assert_().that(template.requestBody("ignite-queue:def?operation=OFFER&capacity=100", "hello101", boolean.class)).isFalse();

        final CountDownLatch latch = new CountDownLatch(1);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                assert_().that(template.requestBody("ignite-queue:def?operation=PUT&capacity=100", "hello101", boolean.class)).isFalse();
                latch.countDown();
            }
        });

        t.start();

        // Wait 2 seconds and check that the thread was blocked.
        assert_().that(latch.await(2000, TimeUnit.MILLISECONDS)).isFalse();
        t.interrupt();

        // PEEK and ELEMENT.
        assert_().that(template.requestBody("ignite-queue:def?operation=PEEK&capacity=100", null, String.class)).isEqualTo("hello0");
        assert_().that(template.requestBody("ignite-queue:def?operation=ELEMENT&capacity=100", null, String.class)).isEqualTo("hello0");

        // TAKE.
        assert_().that(template.requestBody("ignite-queue:def?operation=TAKE&capacity=100", null, String.class)).isEqualTo("hello0");
        assert_().that(template.requestBody("ignite-queue:def?operation=SIZE&capacity=100", null, int.class)).isEqualTo(99);

        // Now drain.
        assert_().that(template.requestBody("ignite-queue:def?operation=DRAIN&capacity=100", null, String[].class)).asList().hasSize(99);
        assert_().that(template.requestBody("ignite-queue:def?operation=SIZE&capacity=100", null, int.class)).isEqualTo(0);
        assert_().that(template.requestBody("ignite-queue:def?operation=POLL&capacity=100", null, String.class)).isNull();

        // TAKE.
        t = new Thread(new Runnable() {
            @Override
            public void run() {
                assert_().that(template.requestBody("ignite-queue:def?operation=TAKE&capacity=100", null, String.class)).isEqualTo("hello102");
                latch.countDown();
            }
        });

        t.start();

        // Element was returned.
        assert_().that(template.requestBody("ignite-queue:def?operation=ADD&capacity=100", "hello102", boolean.class)).isTrue();
        assert_().that(latch.await(1000, TimeUnit.MILLISECONDS)).isTrue();

        // POLL with a timeout.
        assert_().that(Executors.newSingleThreadExecutor().submit(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                Stopwatch sw = Stopwatch.createStarted();
                assert_().that(template.requestBody("ignite-queue:def?operation=POLL&timeoutMillis=1000&capacity=100", null, String.class)).isNull();
                return sw.elapsed(TimeUnit.MILLISECONDS);
            }
        }).get()).isAtLeast(1000L);

    }

    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @After
    public void deleteQueues() {
        for (String queueName : ImmutableSet.<String> of("abc")) {
            ignite().queue(queueName, 0, new CollectionConfiguration()).close();
        }

        // Bounded queues.
        for (String queueName : ImmutableSet.<String> of("def")) {
            ignite().queue(queueName, 100, new CollectionConfiguration()).close();
        }
    }

}
