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
package org.apache.camel.catalog;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.catalog.impl.DefaultRuntimeCamelCatalog;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class RuntimeCamelCatalogTest {

    static RuntimeCamelCatalog catalog;

    @BeforeClass
    public static void createCamelCatalog() {
        catalog = new DefaultRuntimeCamelCatalog();
        catalog.setCamelContext(new DefaultCamelContext());
    }

    @Test
    public void testAsEndpointUriMapFile() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("directoryName", "src/data/inbox");
        map.put("noop", "true");
        map.put("delay", "5000");

        String uri = catalog.asEndpointUri("file", map, true);
        assertEquals("file:src/data/inbox?delay=5000&noop=true", uri);
    }

    @Test
    public void testAsEndpointUriTimer() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("timerName", "foo");
        map.put("period", "5000");

        String uri = catalog.asEndpointUri("timer", map, true);
        assertEquals("timer:foo?period=5000", uri);
    }

    @Test
    public void testAsEndpointUriPropertiesPlaceholders() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("timerName", "foo");
        map.put("period", "{{howoften}}");
        map.put("repeatCount", "5");

        String uri = catalog.asEndpointUri("timer", map, true);
        assertEquals("timer:foo?period=%7B%7Bhowoften%7D%7D&repeatCount=5", uri);

        uri = catalog.asEndpointUri("timer", map, false);
        assertEquals("timer:foo?period={{howoften}}&repeatCount=5", uri);
    }

    @Test
    public void testAsEndpointUriBeanLookup() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("resourceUri", "foo.xslt");
        map.put("converter", "#myConverter");

        String uri = catalog.asEndpointUri("xslt", map, true);
        assertEquals("xslt:foo.xslt?converter=%23myConverter", uri);

        uri = catalog.asEndpointUri("xslt", map, false);
        assertEquals("xslt:foo.xslt?converter=#myConverter", uri);
    }

    @Test
    public void testEndpointPropertiesPlaceholders() throws Exception {
        Map<String, String> map = catalog.endpointProperties("timer:foo?period={{howoften}}&repeatCount=5");
        assertNotNull(map);
        assertEquals(3, map.size());

        assertEquals("foo", map.get("timerName"));
        assertEquals("{{howoften}}", map.get("period"));
        assertEquals("5", map.get("repeatCount"));
    }

    @Test
    public void testAsEndpointUriLog() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("loggerName", "foo");
        map.put("loggerLevel", "WARN");
        map.put("multiline", "true");
        map.put("showAll", "true");
        map.put("showBody", "false");
        map.put("showBodyType", "false");
        map.put("showExchangePattern", "false");
        map.put("style", "Tab");

        assertEquals("log:foo?loggerLevel=WARN&multiline=true&showAll=true&style=Tab", catalog.asEndpointUri("log", map, false));
    }

    @Test
    public void testAsEndpointUriLogShort() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("loggerName", "foo");
        map.put("loggerLevel", "DEBUG");

        assertEquals("log:foo?loggerLevel=DEBUG", catalog.asEndpointUri("log", map, false));
    }

    @Test
    public void testAsEndpointUriWithplaceholder() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("name", "foo");
        map.put("blockWhenFull", "{{block}}");
        assertEquals("seda:foo?blockWhenFull={{block}}", catalog.asEndpointUri("seda", map, false));
    }

    @Test
    public void testEndpointPropertiesSedaRequired() throws Exception {
        Map<String, String> map = catalog.endpointProperties("seda:foo");
        assertNotNull(map);
        assertEquals(1, map.size());

        assertEquals("foo", map.get("name"));

        map = catalog.endpointProperties("seda:foo?blockWhenFull=true");
        assertNotNull(map);
        assertEquals(2, map.size());

        assertEquals("foo", map.get("name"));
        assertEquals("true", map.get("blockWhenFull"));
    }

}
