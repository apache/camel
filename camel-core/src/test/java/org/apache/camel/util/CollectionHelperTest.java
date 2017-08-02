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
package org.apache.camel.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

/**
 * @version 
 */
public class CollectionHelperTest extends TestCase {

    private String[] names = new String[]{"Claus", "Willem", "Jonathan"};
    private List<String> list = Arrays.asList(names);

    public void testCollectionAsCommaDelimitedString() {
        assertEquals("Claus,Willem,Jonathan", CollectionHelper.collectionAsCommaDelimitedString(names));
        assertEquals("Claus,Willem,Jonathan", CollectionHelper.collectionAsCommaDelimitedString(list));

        assertEquals("", CollectionHelper.collectionAsCommaDelimitedString((String[]) null));
        assertEquals("", CollectionHelper.collectionAsCommaDelimitedString((Collection<?>) null));

        assertEquals("Claus", CollectionHelper.collectionAsCommaDelimitedString(new String[]{"Claus"}));
    }

    public void testSize() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("foo", 123);
        map.put("bar", 456);

        assertEquals(2, CollectionHelper.size(map).intValue());

        String[] array = new String[]{"Claus", "Willem"};
        assertEquals(2, CollectionHelper.size(array).intValue());
    }

    public void testAppendValue() {
        Map<String, Object> map = new HashMap<String, Object>();
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

    public void testCreateSetContaining() throws Exception {
        Set<String> set = CollectionHelper.createSetContaining("foo", "bar", "baz");
        assertEquals(3, set.size());
        assertTrue(set.contains("foo"));
        assertTrue(set.contains("bar"));
        assertTrue(set.contains("baz"));
    }

    public void testFlatternKeysInMap() throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> api = new LinkedHashMap<>();
        Map<String, Object> contact = new LinkedHashMap<>();
        contact.put("organization", "Apache Software Foundation");
        api.put("version", "1.0.0");
        api.put("title", "My cool API");
        api.put("contact", contact);
        root.put("api", api);
        root.put("cors", true);

        Map<String, Object> flattern = CollectionHelper.flatternKeysInMap(root, ".");
        assertEquals(4, flattern.size());
        assertEquals(true, flattern.get("cors"));
        assertEquals("1.0.0", flattern.get("api.version"));
        assertEquals("My cool API", flattern.get("api.title"));
        assertEquals("Apache Software Foundation", flattern.get("api.contact.organization"));
    }

}
