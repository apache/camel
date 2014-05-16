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
package org.apache.camel.model;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import junit.framework.TestCase;

public class UriOptionTest extends TestCase {
    public void testTransformOptions() {
        List<UriOption> list = Arrays.asList(new UriOption("foo", "123"), new UriOption("bar", "456"));

        Map<String, Object> map = UriOption.transformOptions(list);

        assertEquals(2, map.size());
        assertEquals("123", map.get("foo"));
        assertEquals("456", map.get("bar"));
    }

    public void testTransformOptionsWithNull() {
        assertNull(UriOption.transformOptions(null));
    }

    public void testTransformOptionsWithDuplicate() {
        List<UriOption> list = Arrays.asList(new UriOption("foo", "123"), new UriOption("foo", "456"));

        Map<String, Object> map = UriOption.transformOptions(list);

        assertEquals(1, map.size());
        assertEquals("456", map.get("foo"));
    }
}
