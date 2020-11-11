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

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.camel.component.ignite.set.IgniteSetComponent;
import org.apache.camel.component.ignite.set.IgniteSetEndpoint;
import org.apache.camel.component.ignite.set.IgniteSetOperation;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.CollectionConfiguration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IgniteSetTest extends AbstractIgniteTest {

    @Override
    protected String getScheme() {
        return "ignite-set";
    }

    @Override
    protected AbstractIgniteComponent createComponent() {
        return IgniteSetComponent.fromConfiguration(createConfiguration());
    }

    @Test
    public void testOperations() {
        boolean result = template.requestBody("ignite-set:" + resourceUid + "?operation=ADD", "hello", boolean.class);
        Assertions.assertThat(result).isTrue();
        Assertions.assertThat(ignite().set(resourceUid, new CollectionConfiguration()).contains("hello")).isTrue();

        result = template.requestBody("ignite-set:" + resourceUid + "?operation=CONTAINS", "hello", boolean.class);
        Assertions.assertThat(result).isTrue();
        Assertions.assertThat(ignite().set(resourceUid, new CollectionConfiguration()).contains("hello")).isTrue();

        result = template.requestBody("ignite-set:" + resourceUid + "?operation=REMOVE", "hello", boolean.class);
        Assertions.assertThat(result).isTrue();
        Assertions.assertThat(ignite().set(resourceUid, new CollectionConfiguration()).contains("hello")).isFalse();

        result = template.requestBody("ignite-set:" + resourceUid + "?operation=CONTAINS", "hello", boolean.class);
        Assertions.assertThat(result).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testOperations2() {
        for (int i = 0; i < 100; i++) {
            template.requestBody("ignite-set:" + resourceUid + "?operation=ADD", "hello" + i);
        }

        // SIZE
        int size = template.requestBody("ignite-set:" + resourceUid + "?operation=SIZE", "hello", int.class);
        Assertions.assertThat(size).isEqualTo(100);
        Assertions.assertThat(ignite().set(resourceUid, new CollectionConfiguration()).size()).isEqualTo(100);

        List<String> toRetain = Lists.newArrayList();
        for (int i = 0; i < 50; i++) {
            toRetain.add("hello" + i);
        }

        // RETAIN_ALL
        boolean retained = template.requestBodyAndHeader("ignite-set:" + resourceUid + "?operation=CLEAR", toRetain,
                IgniteConstants.IGNITE_SETS_OPERATION,
                IgniteSetOperation.RETAIN_ALL, boolean.class);
        Assertions.assertThat(retained).isTrue();

        // SIZE
        size = template.requestBody("ignite-set:" + resourceUid + "?operation=SIZE", "hello", int.class);
        Assertions.assertThat(size).isEqualTo(50);
        Assertions.assertThat(ignite().set(resourceUid, new CollectionConfiguration()).size()).isEqualTo(50);

        // ITERATOR
        Iterator<String> iterator
                = template.requestBody("ignite-set:" + resourceUid + "?operation=ITERATOR", "hello", Iterator.class);
        Assertions.assertThat(Iterators.toArray(iterator, String.class)).containsAll(toRetain);

        // ARRAY
        String[] array = template.requestBody("ignite-set:" + resourceUid + "?operation=ARRAY", "hello", String[].class);
        Assertions.assertThat(array).containsAll(toRetain);

        // CLEAR
        Object result = template.requestBody("ignite-set:" + resourceUid + "?operation=CLEAR", "hello", String.class);
        Assertions.assertThat(result).isEqualTo("hello");
        Assertions.assertThat(ignite().set(resourceUid, new CollectionConfiguration()).size()).isEqualTo(0);

        // SIZE
        size = template.requestBody("ignite-set:" + resourceUid + "?operation=SIZE", "hello", int.class);
        Assertions.assertThat(size).isEqualTo(0);
        Assertions.assertThat(ignite().set(resourceUid, new CollectionConfiguration()).size()).isEqualTo(0);
    }

    @Test
    public void testRetainSingle() {
        // Fill data.
        for (int i = 0; i < 100; i++) {
            template.requestBody("ignite-set:" + resourceUid + "?operation=ADD", "hello" + i);
        }

        boolean retained
                = template.requestBody("ignite-set:" + resourceUid + "?operation=RETAIN_ALL", "hello10", boolean.class);
        Assertions.assertThat(retained).isTrue();

        // ARRAY
        String[] array = template.requestBody("ignite-set:" + resourceUid + "?operation=ARRAY", "hello", String[].class);
        Assertions.assertThat(array).containsExactly("hello10");
    }

    @Test
    public void testCollectionsAsCacheObject() {
        // Fill data.
        for (int i = 0; i < 100; i++) {
            template.requestBody("ignite-set:" + resourceUid + "?operation=ADD", "hello" + i);
        }

        // Add the set.
        Set<String> toAdd = Sets.newHashSet("hello101", "hello102", "hello103");
        template.requestBody("ignite-set:" + resourceUid + "?operation=ADD&treatCollectionsAsCacheObjects=true", toAdd);

        // Size must be 101, not 103.
        int size = template.requestBody("ignite-set:" + resourceUid + "?operation=SIZE", "hello", int.class);
        Assertions.assertThat(size).isEqualTo(101);
        Assertions.assertThat(ignite().set(resourceUid, new CollectionConfiguration()).size()).isEqualTo(101);
        Assertions.assertThat(ignite().set(resourceUid, new CollectionConfiguration()).contains(toAdd)).isTrue();

        // Check whether the Set contains the Set.
        boolean contains = template.requestBody(
                "ignite-set:" + resourceUid + "?operation=CONTAINS&treatCollectionsAsCacheObjects=true", toAdd, boolean.class);
        Assertions.assertThat(contains).isTrue();

        // Delete the Set.
        template.requestBody("ignite-set:" + resourceUid + "?operation=REMOVE&treatCollectionsAsCacheObjects=true", toAdd);

        // Size must be 100 again.
        size = template.requestBody("ignite-set:" + resourceUid + "?operation=SIZE", "hello", int.class);
        Assertions.assertThat(size).isEqualTo(100);
        Assertions.assertThat(ignite().set(resourceUid, new CollectionConfiguration()).size()).isEqualTo(100);
        Assertions.assertThat(ignite().set(resourceUid, new CollectionConfiguration()).contains(toAdd)).isFalse();

    }

    @Test
    public void testWithConfiguration() {
        CollectionConfiguration configuration = new CollectionConfiguration();
        configuration.setCacheMode(CacheMode.PARTITIONED);

        context.getRegistry().bind("config", configuration);

        IgniteSetEndpoint igniteEndpoint = context.getEndpoint(
                "ignite-" + "set:" + resourceUid + "?operation=ADD&configuration=#config", IgniteSetEndpoint.class);
        template.requestBody(igniteEndpoint, "hello");

        Assertions.assertThat(ignite().set(resourceUid, configuration).size()).isEqualTo(1);
        Assertions.assertThat(igniteEndpoint.getConfiguration()).isEqualTo(configuration);
    }

    @AfterEach
    public void deleteSet() {
        ignite().set(resourceUid, null).close();
    }
}
