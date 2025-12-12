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

import java.util.Iterator;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CamelCaseOrderedPropertiesTest {

    @Test
    public void testOrdered() {
        Properties prop = new CamelCaseOrderedProperties();
        prop.setProperty("hello-world", "Hi Camel");
        prop.setProperty("camel.main.stream-caching-enabled", "true");

        assertEquals(2, prop.size());

        Iterator<Object> it = prop.keySet().iterator();
        assertEquals("hello-world", it.next());
        assertEquals("camel.main.stream-caching-enabled", it.next());

        it = prop.values().iterator();
        assertEquals("Hi Camel", it.next());
        assertEquals("true", it.next());

        assertEquals("Hi Camel", prop.getProperty("hello-world"));
        assertEquals("Hi Camel", prop.getProperty("helloWorld"));
        assertEquals("true", prop.getProperty("camel.main.stream-caching-enabled"));
        assertEquals("true", prop.getProperty("camel.main.streamCachingEnabled"));

        assertEquals("Davs", prop.getProperty("bye-world", "Davs"));
    }

    @Test
    public void testOrderedLoad() throws Exception {
        Properties prop = new CamelCaseOrderedProperties();
        prop.load(CamelCaseOrderedPropertiesTest.class.getResourceAsStream("/application.properties"));

        assertEquals(4, prop.size());

        Iterator<Object> it = prop.keySet().iterator();
        assertEquals("hello", it.next());
        assertEquals("camel.component.seda.concurrent-consumers", it.next());
        assertEquals("camel.component.seda.queueSize", it.next());
        assertEquals("camel.component.direct.timeout", it.next());

        // should be ordered values
        it = prop.values().iterator();
        assertEquals("World", it.next());
        assertEquals("2", it.next());
        assertEquals("500", it.next());
        assertEquals("1234", it.next());

        assertEquals("World", prop.getProperty("hello", "MyDefault"));
        assertEquals("2", prop.getProperty("camel.component.seda.concurrent-consumers", "MyDefault"));
        assertEquals("2", prop.getProperty("camel.component.seda.concurrentConsumers", "MyDefault"));
        assertEquals("500", prop.getProperty("camel.component.seda.queue-size", "MyDefault"));
        assertEquals("500", prop.getProperty("camel.component.seda.queueSize", "MyDefault"));
        assertEquals("1234", prop.getProperty("camel.component.direct.timeout", "MyDefault"));
    }

}
