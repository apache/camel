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
package org.apache.camel.catalog.connector;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CamelConnectorCatalogTest {

    @Test
    public void testAddConnector() throws Exception {
        CamelConnectorCatalog catalog = new DefaultCamelConnectorCatalog();

        assertEquals(0, catalog.findConnector(false).size());

        catalog.addConnector("org.apache.camel", "myfoo-connector", "2.19.0",
            "MyFoo", "my-foo", "org.myfoo.connector.MyFooComponent",
            "Something cool", "foo,timer", null, null, null);

        assertEquals(1, catalog.findConnector(false).size());
    }

    @Test
    public void testHasConnector() throws Exception {
        CamelConnectorCatalog catalog = new DefaultCamelConnectorCatalog();

        assertEquals(0, catalog.findConnector(false).size());

        assertFalse(catalog.hasConnector("org.apache.camel", "myfoo-connector", "2.19.0"));

        catalog.addConnector("org.apache.camel", "myfoo-connector", "2.19.0",
            "MyFoo", "my-foo", "org.myfoo.connector.MyFooComponent",
            "Something cool", "foo,timer", null, null, null);

        assertEquals(1, catalog.findConnector(false).size());

        assertTrue(catalog.hasConnector("org.apache.camel", "myfoo-connector", "2.19.0"));
    }

    @Test
    public void testConnectorJson() throws Exception {
        CamelConnectorCatalog catalog = new DefaultCamelConnectorCatalog();

        catalog.addConnector("org.apache.camel", "myfoo-connector", "2.19.0",
            "MyFoo", "my-foo", "org.myfoo.connector.MyFooComponent",
            "Something cool", "foo,timer", "foo", "bar", "baz");

        String json = catalog.connectorJSon("org.apache.camel", "myfoo-connector", "2.19.0");
        assertEquals("foo", json);
    }

    @Test
    public void testConnectorSchemaJson() throws Exception {
        CamelConnectorCatalog catalog = new DefaultCamelConnectorCatalog();

        catalog.addConnector("org.apache.camel", "myfoo-connector", "2.19.0",
            "MyFoo", "my-foo", "org.myfoo.connector.MyFooComponent",
            "Something cool", "foo,timer", "foo", "bar", "baz");

        String json = catalog.connectorSchemaJSon("org.apache.camel", "myfoo-connector", "2.19.0");
        assertEquals("bar", json);
    }

    @Test
    public void testComponentSchemaJson() throws Exception {
        CamelConnectorCatalog catalog = new DefaultCamelConnectorCatalog();

        catalog.addConnector("org.apache.camel", "myfoo-connector", "2.19.0",
            "MyFoo", "my-foo", "org.myfoo.connector.MyFooComponent",
            "Something cool", "foo,timer", "foo", "bar", "baz");

        String json = catalog.componentSchemaJSon("org.apache.camel", "myfoo-connector", "2.19.0");
        assertEquals("baz", json);
    }

    @Test
    public void testRemoveConnector() throws Exception {
        CamelConnectorCatalog catalog = new DefaultCamelConnectorCatalog();

        assertEquals(0, catalog.findConnector(false).size());

        catalog.addConnector("org.apache.camel", "myfoo-connector", "2.19.0",
            "MyFoo", "my-foo", "org.myfoo.connector.MyFooComponent",
            "Something cool", "foo,timer", null, null, null);

        assertEquals(1, catalog.findConnector(false).size());

        catalog.removeConnector("org.apache.camel", "myfoo-connector", "2.19.0");

        assertEquals(0, catalog.findConnector(false).size());
    }

    @Test
    public void testFindConnectorFilter() throws Exception {
        CamelConnectorCatalog catalog = new DefaultCamelConnectorCatalog();

        assertEquals(0, catalog.findConnector(false).size());

        catalog.addConnector("org.apache.camel", "myfoo-connector", "2.19.0",
            "MyFoo", "my-foo", "org.myfoo.connector.MyFooComponent",
            "Something cool", "foo,timer", null, null, null);

        assertEquals(1, catalog.findConnector("foo", false).size());
        assertEquals(0, catalog.findConnector("bar", false).size());
    }

    @Test
    public void testFindConnectorLatestVersionOnly() throws Exception {
        CamelConnectorCatalog catalog = new DefaultCamelConnectorCatalog();

        assertEquals(0, catalog.findConnector(false).size());

        catalog.addConnector("org.apache.camel", "myfoo-connector", "2.19.0",
            "MyFoo", "my-foo", "org.myfoo.connector.MyFooComponent",
            "Something cool", "foo,timer", null, null, null);

        catalog.addConnector("org.apache.camel", "myfoo-connector", "2.19.1",
            "MyFoo", "my-foo", "org.myfoo.connector.MyFooComponent",
            "Something more cool", "foo,timer", null, null, null);

        assertEquals(1, catalog.findConnector("foo", true).size());
        assertEquals(0, catalog.findConnector("bar", true).size());

        assertEquals("2.19.1", catalog.findConnector("foo", true).get(0).getVersion());
        assertEquals("Something more cool", catalog.findConnector("foo", true).get(0).getDescription());
    }

    @Test
    public void testFindConnectorNotLatestVersionOnly() throws Exception {
        CamelConnectorCatalog catalog = new DefaultCamelConnectorCatalog();

        assertEquals(0, catalog.findConnector(false).size());

        catalog.addConnector("org.apache.camel", "myfoo-connector", "2.19.0",
            "MyFoo", "my-foo", "org.myfoo.connector.MyFooComponent",
            "Something cool", "foo,timer", null, null, null);

        catalog.addConnector("org.apache.camel", "myfoo-connector", "2.19.1",
            "MyFoo", "my-foo", "org.myfoo.connector.MyFooComponent",
            "Something more cool", "foo,timer", null, null, null);

        assertEquals(2, catalog.findConnector("foo", false).size());
        assertEquals(0, catalog.findConnector("bar", false).size());

        assertEquals("2.19.0", catalog.findConnector("foo", false).get(0).getVersion());
        assertEquals("Something cool", catalog.findConnector("foo", false).get(0).getDescription());
        assertEquals("2.19.1", catalog.findConnector("foo", false).get(1).getVersion());
        assertEquals("Something more cool", catalog.findConnector("foo", false).get(1).getDescription());
    }

}
