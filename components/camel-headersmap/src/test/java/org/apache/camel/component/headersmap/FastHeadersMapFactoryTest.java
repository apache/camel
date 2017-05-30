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
package org.apache.camel.component.headersmap;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

public class FastHeadersMapFactoryTest extends TestCase {

    public void testLookupCaseAgnostic() {
        Map<String, Object> map = new FastHeadersMapFactory().newMap();
        assertNull(map.get("foo"));

        map.put("foo", "cheese");

        assertEquals("cheese", map.get("foo"));
        assertEquals("cheese", map.get("Foo"));
        assertEquals("cheese", map.get("FOO"));
    }

    public void testConstructFromOther() {
        Map<String, Object> other = new FastHeadersMapFactory().newMap();
        other.put("Foo", "cheese");
        other.put("bar", 123);

        Map<String, Object> map = new FastHeadersMapFactory().newMap(other);

        assertEquals("cheese", map.get("FOO"));
        assertEquals("cheese", map.get("foo"));
        assertEquals("cheese", map.get("Foo"));

        assertEquals(123, map.get("BAR"));
        assertEquals(123, map.get("bar"));
        assertEquals(123, map.get("BaR"));
    }

    public void testIsInstance() {
        Map<String, Object> map = new FastHeadersMapFactory().newMap();

        Map<String, Object> other = new FastHeadersMapFactory().newMap(map);
        other.put("Foo", "cheese");
        other.put("bar", 123);

        assertTrue(new FastHeadersMapFactory().isInstanceOf(map));
        assertTrue(new FastHeadersMapFactory().isInstanceOf(other));
        assertFalse(new FastHeadersMapFactory().isInstanceOf(new HashMap<>()));
    }

}
