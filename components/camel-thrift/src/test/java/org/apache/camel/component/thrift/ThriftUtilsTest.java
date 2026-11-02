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
package org.apache.camel.component.thrift;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A body that is not a List is the one argument of the method, and is matched to its parameter type with the same rules
 * as the arguments in a List body.
 */
public class ThriftUtilsTest {

    @Test
    public void testSingleIntegerBodyCallsMethodWithOneIntArgument() {
        assertEquals(42, ThriftUtils.invokeSyncMethod(new FakeClient(), "twice", 21));
        assertEquals(42, ThriftUtils.invokeSyncMethod(new FakeClient(), "twice", List.of(21)));
    }

    @Test
    public void testSingleBoxedBodiesCallMethodsWithOnePrimitiveArgument() {
        assertEquals(42L, ThriftUtils.invokeSyncMethod(new FakeClient(), "twiceLong", 21L));
        assertEquals(false, ThriftUtils.invokeSyncMethod(new FakeClient(), "not", true));
    }

    @Test
    public void testSingleMapOrSetBodyCallsMethodWithOneMapOrSetArgument() {
        Map<String, Integer> map = new HashMap<>();
        map.put("a", 1);
        assertEquals(1, ThriftUtils.invokeSyncMethod(new FakeClient(), "size", map));
        Set<String> set = new HashSet<>(Arrays.asList("a", "b"));
        assertEquals(2, ThriftUtils.invokeSyncMethod(new FakeClient(), "count", set));
    }

    @Test
    public void testNullArgumentInListIsReportedAsMethodNotFound() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ThriftUtils.invokeSyncMethod(new FakeClient(), "twice", Arrays.asList((Object) null)));
        assertTrue(e.getMessage().endsWith(".twice(null)"), e.getMessage());
    }

    public static class FakeClient {

        public int twice(int value) {
            return value * 2;
        }

        public long twiceLong(long value) {
            return value * 2;
        }

        public boolean not(boolean value) {
            return !value;
        }

        public int size(Map<String, Integer> value) {
            return value.size();
        }

        public int count(Set<String> value) {
            return value.size();
        }
    }
}
