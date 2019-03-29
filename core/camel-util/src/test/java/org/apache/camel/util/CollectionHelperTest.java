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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

public class CollectionHelperTest extends Assert {

    @Test
    public void testCollectionAsCommaDelimitedString() {
        assertEquals("Claus,Willem,Jonathan", CollectionHelper.collectionAsCommaDelimitedString(Arrays.asList("Claus", "Willem", "Jonathan")));

        assertEquals("", CollectionHelper.collectionAsCommaDelimitedString(null));

        assertEquals("Claus", CollectionHelper.collectionAsCommaDelimitedString(Collections.singletonList("Claus")));
    }

    @Test
    public void testSize() {
        Map<String, Object> map = new HashMap<>();
        map.put("foo", 123);
        map.put("bar", 456);

        assertEquals(2, CollectionHelper.size(map).intValue());

        String[] array = new String[]{"Claus", "Willem"};
        assertEquals(2, CollectionHelper.size(array).intValue());
    }

    @Test
    public void testAppendValue() {
        Map<String, Object> map = new HashMap<>();
        CollectionHelper.appendValue(map, "foo", 123);
        assertEquals(1, map.size());

        CollectionHelper.appendValue(map, "foo", 456);
        assertEquals(1, map.size());

        CollectionHelper.appendValue(map, "bar", 789);
        assertEquals(2, map.size());

        List<?> values = (List<?>) map.get("foo");
        assertEquals(2, values.size());
        assertEquals(123, values.get(0));
        assertEquals(456, values.get(1));

        Integer value = (Integer) map.get("bar");
        assertEquals(789, value.intValue());
    }

    @Test
    public void testCreateSetContaining() throws Exception {
        Set<String> set = CollectionHelper.createSetContaining("foo", "bar", "baz");
        assertEquals(3, set.size());
        assertTrue(set.contains("foo"));
        assertTrue(set.contains("bar"));
        assertTrue(set.contains("baz"));
    }

    @Test
    public void testflattenKeysInMap() throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> api = new LinkedHashMap<>();
        Map<String, Object> contact = new LinkedHashMap<>();
        contact.put("organization", "Apache Software Foundation");
        api.put("version", "1.0.0");
        api.put("title", "My cool API");
        api.put("contact", contact);
        root.put("api", api);
        root.put("cors", true);

        Map<String, Object> flattern = CollectionHelper.flattenKeysInMap(root, ".");
        assertEquals(4, flattern.size());
        assertEquals(true, flattern.get("cors"));
        assertEquals("1.0.0", flattern.get("api.version"));
        assertEquals("My cool API", flattern.get("api.title"));
        assertEquals("Apache Software Foundation", flattern.get("api.contact.organization"));
    }

}
