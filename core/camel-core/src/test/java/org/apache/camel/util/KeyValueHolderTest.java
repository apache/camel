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

import org.junit.Assert;
import org.junit.Test;

public class KeyValueHolderTest extends Assert {

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

        assertTrue("Should be equals", foo1.equals(foo2));
        assertTrue("Hash code should be equal", foo1.hashCode() == foo2.hashCode());
    }

    @Test
    public void testEqualsAndHashCodeOnUnequalObjects() {
        KeyValueHolder<String, Integer> foo = new KeyValueHolder<>("foo", 123);
        KeyValueHolder<String, Integer> bar = new KeyValueHolder<>("bar", 678);

        assertFalse("Should not be equals", foo.equals(bar));
        assertFalse("Hash code should not be equal", foo.hashCode() == bar.hashCode());
    }

    @Test
    public void testEqualsAndHashCodeOnUnequalObjectsWithSameKeys() {
        KeyValueHolder<String, Integer> foo1 = new KeyValueHolder<>("foo", 123);
        KeyValueHolder<String, Integer> foo2 = new KeyValueHolder<>("foo", 678);

        assertFalse("Should not be equals", foo1.equals(foo2));
        assertFalse("Hash code should not be equal", foo1.hashCode() == foo2.hashCode());
    }

    @Test
    public void testEqualsAndHashCodeOnUnequalObjectsWithSameValues() {
        KeyValueHolder<String, Integer> foo = new KeyValueHolder<>("foo", 123);
        KeyValueHolder<String, Integer> bar = new KeyValueHolder<>("bar", 123);

        assertFalse("Should not be equals", foo.equals(bar));
        assertFalse("Hash code should not be equal", foo.hashCode() == bar.hashCode());
    }

    @Test
    public void testToString() {
        KeyValueHolder<String, Integer> foo = new KeyValueHolder<>("foo", 123);

        assertEquals("foo -> 123", foo.toString());
    }
}
