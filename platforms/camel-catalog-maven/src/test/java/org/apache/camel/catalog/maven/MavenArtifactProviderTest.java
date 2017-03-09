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
package org.apache.camel.catalog.maven;

import java.util.List;
import java.util.Set;

import junit.framework.TestCase;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.connector.CamelConnectorCatalog;
import org.apache.camel.catalog.connector.ConnectorDto;
import org.apache.camel.catalog.connector.DefaultCamelConnectorCatalog;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Cannot run on CI servers so run manually")
public class MavenArtifactProviderTest extends TestCase {

    @Test
    public void testAddComponent() {
        CamelCatalog camelCatalog = new DefaultCamelCatalog();
        MavenArtifactProvider provider = new DefaultMavenArtifactProvider();
        provider.setCacheDirectory("target/cache");

        int before = camelCatalog.findComponentNames().size();

        Set<String> names = provider.addArtifactToCatalog(camelCatalog, null, "org.apache.camel", "dummy-component", camelCatalog.getCatalogVersion());
        assertTrue(names.contains("dummy"));

        int after = camelCatalog.findComponentNames().size();

        assertTrue("Should find 1 new component", after - before == 1);
    }

    @Test
    public void testAddConnector() {
        CamelCatalog camelCatalog = new DefaultCamelCatalog();
        CamelConnectorCatalog camelConnectorCatalog = new DefaultCamelConnectorCatalog();
        MavenArtifactProvider provider = new DefaultMavenArtifactProvider();
        provider.setCacheDirectory("target/cache");

        int before = camelCatalog.findComponentNames().size();
        List<ConnectorDto> list = camelConnectorCatalog.findConnector("foo", false);
        assertEquals(0, list.size());

        Set<String> names = provider.addArtifactToCatalog(camelCatalog, camelConnectorCatalog, "org.apache.camel", "myfoo-connector", camelCatalog.getCatalogVersion());
        assertTrue(names.contains("MyFoo"));

        int after = camelCatalog.findComponentNames().size();

        assertTrue("Should find 1 new component", after - before == 1);

        list = camelConnectorCatalog.findConnector("foo", false);
        assertEquals(1, list.size());
        assertEquals("MyFoo", list.get(0).getName());
        assertTrue(camelCatalog.findComponentNames().contains("my-foo"));
    }

}
