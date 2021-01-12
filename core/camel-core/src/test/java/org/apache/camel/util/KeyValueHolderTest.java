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
package org.apache.camel.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class KeyValueHolderTest {

    @Test
    public void testKeyValueHolder() {
        KeyValueHolder<String, Integer> foo = new KeyValueHolder<>("foo", 123);

        assertEquals("foo", foo.getKey());
        assertEquals(123, foo.getValue().intValue());
    }

    @Test
    public void testEqualsAndHashCodeOnEqualObjects() {
        KeyValueHolder<String, Integer> foo1 = new KeyValueHolder<>("foo", 123);
        KeyValueHolder<String, Integer> foo2 = new KeyValueHolder<>("foo", 123);

        assertEquals(foo2, foo1, "Should be equals");
        assertEquals(foo2.hashCode(), foo1.hashCode(), "Hash code should be equal");
    }

    @Test
    public void testEqualsAndHashCodeOnUnequalObjects() {
        KeyValueHolder<String, Integer> foo = new KeyValueHolder<>("foo", 123);
        KeyValueHolder<String, Integer> bar = new KeyValueHolder<>("bar", 678);

        assertNotEquals(bar, foo, "Should not be equals");
        assertNotEquals(bar.hashCode(), foo.hashCode(), "Hash code should not be equal");
    }

    @Test
    public void testEqualsAndHashCodeOnUnequalObjectsWithSameKeys() {
        KeyValueHolder<String, Integer> foo1 = new KeyValueHolder<>("foo", 123);
        KeyValueHolder<String, Integer> foo2 = new KeyValueHolder<>("foo", 678);

        assertNotEquals(foo2, foo1, "Should not be equals");
        assertNotEquals(foo2.hashCode(), foo1.hashCode(), "Hash code should not be equal");
    }

    @Test
    public void testEqualsAndHashCodeOnUnequalObjectsWithSameValues() {
        KeyValueHolder<String, Integer> foo = new KeyValueHolder<>("foo", 123);
        KeyValueHolder<String, Integer> bar = new KeyValueHolder<>("bar", 123);

        assertNotEquals(bar, foo, "Should not be equals");
        assertNotEquals(bar.hashCode(), foo.hashCode(), "Hash code should not be equal");
    }

    @Test
    public void testToString() {
        KeyValueHolder<String, Integer> foo = new KeyValueHolder<>("foo", 123);

        assertEquals("foo -> 123", foo.toString());
    }
}
