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
package org.apache.camel.catalog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;
import org.junit.Test;

import static org.apache.camel.catalog.CatalogHelper.loadText;

public class CamelCatalogTest extends TestCase {

    private CamelCatalog catalog = new DefaultCamelCatalog();

    @Test
    public void testFindNames() throws Exception {
        List<String> names = catalog.findComponentNames();
        assertNotNull(names);
        assertTrue(names.contains("file"));
        assertTrue(names.contains("log"));
        assertTrue(names.contains("docker"));
        assertTrue(names.contains("jms"));

        names = catalog.findDataFormatNames();
        assertNotNull(names);
        assertTrue(names.contains("bindy-csv"));
        assertTrue(names.contains("hl7"));
        assertTrue(names.contains("jaxb"));
        assertTrue(names.contains("syslog"));

        names = catalog.findLanguageNames();
        assertNotNull(names);
        assertTrue(names.contains("simple"));
        assertTrue(names.contains("groovy"));
        assertTrue(names.contains("mvel"));

        names = catalog.findModelNames();
        assertNotNull(names);
        assertTrue(names.contains("from"));
        assertTrue(names.contains("to"));
        assertTrue(names.contains("recipientList"));
        assertTrue(names.contains("aggregate"));
        assertTrue(names.contains("split"));
        assertTrue(names.contains("loadBalance"));
    }

    @Test
    public void testJsonSchema() throws Exception {
        String schema = catalog.componentJSonSchema("docker");
        assertNotNull(schema);

        schema = catalog.dataFormatJSonSchema("hl7");
        assertNotNull(schema);

        schema = catalog.languageJSonSchema("groovy");
        assertNotNull(schema);

        schema = catalog.modelJSonSchema("aggregate");
        assertNotNull(schema);
    }

    @Test
    public void testXmlSchema() throws Exception {
        String schema = catalog.blueprintSchemaAsXml();
        assertNotNull(schema);

        schema = catalog.springSchemaAsXml();
        assertNotNull(schema);
    }

    @Test
    public void testArchetypeCatalog() throws Exception {
        String schema = catalog.archetypeCatalogAsXml();
        assertNotNull(schema);
    }

    @Test
    public void testAsEndpointUriMapFile() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        map.put("directoryName", "src/data/inbox");
        map.put("noop", "true");
        map.put("delay", "5000");

        String uri = catalog.asEndpointUri("file", map);
        assertEquals("file:src/data/inbox?delay=5000&noop=true", uri);

        String uri2 = catalog.asEndpointUriXml("file", map);
        assertEquals("file:src/data/inbox?delay=5000&amp;noop=true", uri2);
    }

    @Test
    public void testAsEndpointUriMapFtp() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        map.put("host", "someserver");
        map.put("port", "21");
        map.put("directoryName", "foo");
        map.put("connectTimeout", "5000");

        String uri = catalog.asEndpointUri("ftp", map);
        assertEquals("ftp:someserver:21/foo?connectTimeout=5000", uri);

        String uri2 = catalog.asEndpointUriXml("ftp", map);
        assertEquals("ftp:someserver:21/foo?connectTimeout=5000", uri2);
    }

    @Test
    public void testAsEndpointUriMapJms() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        map.put("destinationType", "queue");
        map.put("destinationName", "foo");

        String uri = catalog.asEndpointUri("jms", map);
        assertEquals("jms:queue:foo", uri);
    }

    @Test
    public void testAsEndpointUriMapJmsRequiredOnly() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        map.put("destinationName", "foo");
        String uri = catalog.asEndpointUri("jms", map);
        assertEquals("jms:foo", uri);

        map.put("deliveryPersistent", "false");
        map.put("allowNullBody", "true");

        uri = catalog.asEndpointUri("jms", map);
        assertEquals("jms:foo?allowNullBody=true&deliveryPersistent=false", uri);

        String uri2 = catalog.asEndpointUriXml("jms", map);
        assertEquals("jms:foo?allowNullBody=true&amp;deliveryPersistent=false", uri2);
    }

    @Test
    public void testAsEndpointUriJson() throws Exception {
        String json = loadText(CamelCatalogTest.class.getClassLoader().getResourceAsStream("sample.json"));
        String uri = catalog.asEndpointUri("ftp", json);
        assertEquals("ftp:someserver:21/foo?connectTimeout=5000", uri);
    }

    @Test
    public void testEndpointProperties() throws Exception {
        Map<String, String> map = catalog.endpointProperties("ftp:someserver:21/foo?connectTimeout=5000");
        assertNotNull(map);
        assertEquals(4, map.size());

        assertEquals("someserver", map.get("host"));
        assertEquals("21", map.get("port"));
        assertEquals("foo", map.get("directoryName"));
        assertEquals("5000", map.get("connectTimeout"));
    }

    @Test
    public void testEndpointPropertiesJms() throws Exception {
        Map<String, String> map = catalog.endpointProperties("jms:queue:foo");
        assertNotNull(map);
        assertEquals(2, map.size());

        assertEquals("queue", map.get("destinationType"));
        assertEquals("foo", map.get("destinationName"));
    }

    @Test
    public void testEndpointPropertiesJmsRequired() throws Exception {
        Map<String, String> map = catalog.endpointProperties("jms:foo");
        assertNotNull(map);
        assertEquals(1, map.size());

        assertEquals("foo", map.get("destinationName"));

        map = catalog.endpointProperties("jms:foo?allowNullBody=true&deliveryPersistent=false");
        assertNotNull(map);
        assertEquals(3, map.size());

        assertEquals("foo", map.get("destinationName"));
        assertEquals("true", map.get("allowNullBody"));
        assertEquals("false", map.get("deliveryPersistent"));
    }

    @Test
    public void testEndpointPropertiesAtom() throws Exception {
        Map<String, String> map = catalog.endpointProperties("atom:file:src/test/data/feed.atom");
        assertNotNull(map);
        assertEquals(1, map.size());

        assertEquals("file:src/test/data/feed.atom", map.get("feedUri"));

        map = catalog.endpointProperties("atom:file:src/test/data/feed.atom?splitEntries=false&delay=5000");
        assertNotNull(map);
        assertEquals(3, map.size());

        assertEquals("file:src/test/data/feed.atom", map.get("feedUri"));
        assertEquals("false", map.get("splitEntries"));
        assertEquals("5000", map.get("delay"));
    }

    @Test
    public void testEndpointComponentName() throws Exception {
        String name = catalog.endpointComponentName("jms:queue:foo");
        assertEquals("jms", name);
    }

}
