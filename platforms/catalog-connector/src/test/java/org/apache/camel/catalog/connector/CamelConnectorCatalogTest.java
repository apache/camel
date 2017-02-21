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

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CamelConnectorCatalogTest {

    @Test
    public void testAddConnector() throws Exception {
        CamelConnectorCatalog catalog = new DefaultCamelConnectorCatalog();

        assertEquals(0, catalog.findConnector().size());

        catalog.addConnector("org.apache.camel", "myfoo-connector", "2.19.0",
            "MyFoo", "Something cool", "foo,timer", null, null);

        assertEquals(1, catalog.findConnector().size());
    }

    @Test
    public void testRemoveConnector() throws Exception {
        CamelConnectorCatalog catalog = new DefaultCamelConnectorCatalog();

        assertEquals(0, catalog.findConnector().size());

        catalog.addConnector("org.apache.camel", "myfoo-connector", "2.19.0",
            "MyFoo", "Something cool", "foo,timer", null, null);

        assertEquals(1, catalog.findConnector().size());

        catalog.removeConnector("org.apache.camel", "myfoo-connector", "2.19.0");

        assertEquals(0, catalog.findConnector().size());
    }

    @Ignore("Not implemented yet")
    public void testFindConnector() throws Exception {
        CamelConnectorCatalog catalog = new DefaultCamelConnectorCatalog();

        assertEquals(0, catalog.findConnector().size());

        catalog.addConnector("org.apache.camel", "myfoo-connector", "2.19.0",
            "MyFoo", "Something cool", "foo,timer", null, null);

        assertEquals(1, catalog.findConnector("foo").size());
        assertEquals(0, catalog.findConnector("bar").size());
    }

}
