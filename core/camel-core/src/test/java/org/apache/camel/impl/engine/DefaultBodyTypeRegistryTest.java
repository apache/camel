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
package org.apache.camel.impl.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.apache.camel.spi.BodyTypeRegistry.Kind.DATA;
import static org.apache.camel.spi.BodyTypeRegistry.Kind.NULL;
import static org.apache.camel.spi.BodyTypeRegistry.Kind.PAYLOAD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultBodyTypeRegistryTest {

    @Test
    public void testDistinctTypesAndKindsInTheOrderFirstSeen() {
        DefaultBodyTypeRegistry registry = new DefaultBodyTypeRegistry();
        registry.addBodyType("orders", "to1", "java.lang.String", PAYLOAD);
        registry.addBodyType("orders", "to1", "byte[]", PAYLOAD);
        registry.addBodyType("orders", "to1", "java.lang.String", PAYLOAD);

        assertEquals(List.of("java.lang.String", "byte[]"), registry.getBodyTypes("orders", "to1"));
        // two types of one kind: dynamic, but the steps after it handle both
        assertEquals(List.of(PAYLOAD), registry.getBodyKinds("orders", "to1"));

        registry.addBodyType("orders", "to1", "java.util.LinkedHashMap", DATA);
        assertEquals(List.of(PAYLOAD, DATA), registry.getBodyKinds("orders", "to1"));

        // a node of another route with the same id is another node
        assertTrue(registry.getBodyTypes("invoices", "to1").isEmpty());
        assertTrue(registry.getBodyKinds("invoices", "to1").isEmpty());
    }

    @Test
    public void testANodeKeepsTheFirstFewTypesAndEveryKind() {
        DefaultBodyTypeRegistry registry = new DefaultBodyTypeRegistry();
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < DefaultBodyTypeRegistry.MAX_TYPES_PER_NODE + 3; i++) {
            registry.addBodyType("orders", "to1", "com.foo.Type" + i, DATA);
            if (i < DefaultBodyTypeRegistry.MAX_TYPES_PER_NODE) {
                expected.add("com.foo.Type" + i);
            }
        }
        assertEquals(expected, registry.getBodyTypes("orders", "to1"));

        // a new kind is what needs attention, so it is kept also when the node has no room for more types
        registry.addBodyType("orders", "to1", "null", NULL);
        assertEquals(expected, registry.getBodyTypes("orders", "to1"));
        assertEquals(List.of(DATA, NULL), registry.getBodyKinds("orders", "to1"));
    }

    @Test
    public void testBodyTypesOfARoute() {
        DefaultBodyTypeRegistry registry = new DefaultBodyTypeRegistry();
        registry.addBodyType("orders", "to1", "java.lang.String", PAYLOAD);
        registry.addBodyType("orders", "log1", "java.util.LinkedHashMap", DATA);

        Map<String, List<String>> types = registry.getBodyTypes("orders");
        assertEquals(2, types.size());
        assertEquals(List.of("java.lang.String"), types.get("to1"));
        assertEquals(List.of("java.util.LinkedHashMap"), types.get("log1"));
        assertTrue(registry.getBodyTypes("invoices").isEmpty());
    }

    @Test
    public void testRemoveRouteAndClear() {
        DefaultBodyTypeRegistry registry = new DefaultBodyTypeRegistry();
        registry.addBodyType("orders", "to1", "java.lang.String", PAYLOAD);
        registry.addBodyType("invoices", "to2", "java.lang.String", PAYLOAD);

        registry.removeRoute("orders");
        assertTrue(registry.getBodyTypes("orders", "to1").isEmpty());
        assertEquals(List.of("java.lang.String"), registry.getBodyTypes("invoices", "to2"));

        registry.clear();
        assertTrue(registry.getBodyTypes("invoices", "to2").isEmpty());
    }
}
