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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.apache.camel.component.ignite.set.IgniteSetComponent;
import org.apache.camel.component.ignite.set.IgniteSetEndpoint;
import org.apache.camel.component.ignite.set.IgniteSetOperation;
import org.apache.camel.impl.JndiRegistry;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.CollectionConfiguration;
import org.junit.After;
import org.junit.Test;

import static com.google.common.truth.Truth.assert_;

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
        boolean result = template.requestBody("ignite-set:abc?operation=ADD", "hello", boolean.class);
        assert_().that(result).isTrue();
        assert_().that(ignite().set("abc", new CollectionConfiguration()).contains("hello")).isTrue();

        result = template.requestBody("ignite-set:abc?operation=CONTAINS", "hello", boolean.class);
        assert_().that(result).isTrue();
        assert_().that(ignite().set("abc", new CollectionConfiguration()).contains("hello")).isTrue();

        result = template.requestBody("ignite-set:abc?operation=REMOVE", "hello", boolean.class);
        assert_().that(result).isTrue();
        assert_().that(ignite().set("abc", new CollectionConfiguration()).contains("hello")).isFalse();

        result = template.requestBody("ignite-set:abc?operation=CONTAINS", "hello", boolean.class);
        assert_().that(result).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testOperations2() {
        for (int i = 0; i < 100; i++) {
            template.requestBody("ignite-set:abc?operation=ADD", "hello" + i);
        }

        // SIZE
        int size = template.requestBody("ignite-set:abc?operation=SIZE", "hello", int.class);
        assert_().that(size).isEqualTo(100);
        assert_().that(ignite().set("abc", new CollectionConfiguration()).size()).isEqualTo(100);

        List<String> toRetain = Lists.newArrayList();
        for (int i = 0; i < 50; i++) {
            toRetain.add("hello" + i);
        }

        // RETAIN_ALL
        boolean retained = template.requestBodyAndHeader("ignite-set:abc?operation=CLEAR", toRetain, IgniteConstants.IGNITE_SETS_OPERATION, IgniteSetOperation.RETAIN_ALL, boolean.class);
        assert_().that(retained).isTrue();

        // SIZE
        size = template.requestBody("ignite-set:abc?operation=SIZE", "hello", int.class);
        assert_().that(size).isEqualTo(50);
        assert_().that(ignite().set("abc", new CollectionConfiguration()).size()).isEqualTo(50);

        // ITERATOR
        Iterator<String> iterator = template.requestBody("ignite-set:abc?operation=ITERATOR", "hello", Iterator.class);
        assert_().that(Iterators.toArray(iterator, String.class)).asList().containsExactlyElementsIn(toRetain);

        // ARRAY
        String[] array = template.requestBody("ignite-set:abc?operation=ARRAY", "hello", String[].class);
        assert_().that(array).asList().containsExactlyElementsIn(toRetain);

        // CLEAR
        Object result = template.requestBody("ignite-set:abc?operation=CLEAR", "hello", String.class);
        assert_().that(result).isEqualTo("hello");
        assert_().that(ignite().set("abc", new CollectionConfiguration()).size()).isEqualTo(0);

        // SIZE
        size = template.requestBody("ignite-set:abc?operation=SIZE", "hello", int.class);
        assert_().that(size).isEqualTo(0);
        assert_().that(ignite().set("abc", new CollectionConfiguration()).size()).isEqualTo(0);
    }

    @Test
    public void testRetainSingle() {
        // Fill data.
        for (int i = 0; i < 100; i++) {
            template.requestBody("ignite-set:abc?operation=ADD", "hello" + i);
        }

        boolean retained = template.requestBody("ignite-set:abc?operation=RETAIN_ALL", "hello10", boolean.class);
        assert_().that(retained).isTrue();

        // ARRAY
        String[] array = template.requestBody("ignite-set:abc?operation=ARRAY", "hello", String[].class);
        assert_().that(array).asList().containsExactly("hello10");
    }

    @Test
    public void testCollectionsAsCacheObject() {
        // Fill data.
        for (int i = 0; i < 100; i++) {
            template.requestBody("ignite-set:abc?operation=ADD", "hello" + i);
        }

        // Add the set.
        Set<String> toAdd = Sets.newHashSet("hello101", "hello102", "hello103");
        template.requestBody("ignite-set:abc?operation=ADD&treatCollectionsAsCacheObjects=true", toAdd);

        // Size must be 101, not 103.
        int size = template.requestBody("ignite-set:abc?operation=SIZE", "hello", int.class);
        assert_().that(size).isEqualTo(101);
        assert_().that(ignite().set("abc", new CollectionConfiguration()).size()).isEqualTo(101);
        assert_().that(ignite().set("abc", new CollectionConfiguration()).contains(toAdd)).isTrue();

        // Check whether the Set contains the Set.
        boolean contains = template.requestBody("ignite-set:abc?operation=CONTAINS&treatCollectionsAsCacheObjects=true", toAdd, boolean.class);
        assert_().that(contains).isTrue();

        // Delete the Set.
        template.requestBody("ignite-set:abc?operation=REMOVE&treatCollectionsAsCacheObjects=true", toAdd);

        // Size must be 100 again.
        size = template.requestBody("ignite-set:abc?operation=SIZE", "hello", int.class);
        assert_().that(size).isEqualTo(100);
        assert_().that(ignite().set("abc", new CollectionConfiguration()).size()).isEqualTo(100);
        assert_().that(ignite().set("abc", new CollectionConfiguration()).contains(toAdd)).isFalse();

    }

    @Test
    public void testWithConfiguration() {
        CollectionConfiguration configuration = new CollectionConfiguration();
        configuration.setCacheMode(CacheMode.PARTITIONED);

        context.getRegistry(JndiRegistry.class).bind("config", configuration);

        IgniteSetEndpoint igniteEndpoint = context.getEndpoint("ignite-"
            + "set:abc?operation=ADD&configuration=#config", IgniteSetEndpoint.class);
        template.requestBody(igniteEndpoint, "hello");

        assert_().that(ignite().set("abc", configuration).size()).isEqualTo(1);
        assert_().that(igniteEndpoint.getConfiguration()).isEqualTo(configuration);
    }

    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @After
    public void deleteSets() {
        for (String setName : ImmutableSet.<String> of("abc")) {
            ignite().set(setName, new CollectionConfiguration()).close();
        }
    }

}
