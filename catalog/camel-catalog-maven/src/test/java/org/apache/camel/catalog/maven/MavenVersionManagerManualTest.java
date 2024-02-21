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
package org.apache.camel.catalog.maven;

import java.io.InputStream;
import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.DefaultRuntimeProvider;
import org.apache.camel.catalog.impl.CatalogHelper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Cannot run on CI servers so run manually")
public class MavenVersionManagerManualTest {

    private static final String COMPONENTS_CATALOG = "org/apache/camel/catalog/components.properties";

    @Test
    public void testLoadVersion() throws Exception {
        MavenVersionManager manager = new MavenVersionManager();
        String current = manager.getLoadedVersion();
        assertNull(current);

        boolean loaded = manager.loadVersion("2.17.2");
        assertTrue(loaded);

        assertEquals("2.17.2", manager.getLoadedVersion());

        InputStream is = manager.getResourceAsStream(COMPONENTS_CATALOG);
        assertNotNull(is);
        String text = CatalogHelper.loadText(is);

        // should not contain Camel 2.18 components
        assertFalse(text.contains("servicenow"));
        // but 2.17 components such
        assertTrue(text.contains("nats"));
    }

    @Test
    public void testEndpointOptions217() {
        CamelCatalog catalog = new DefaultCamelCatalog(false);
        catalog.setVersionManager(new MavenVersionManager());
        catalog.loadVersion("2.17.1");

        assertEquals("2.17.1", catalog.getLoadedVersion());

        String json = catalog.componentJSonSchema("ahc");
        assertNotNull(json);

        // should have loaded the 2.17.1 version
        assertTrue(json.contains("\"version\": \"2.17.1\""));

        // should not contain Camel 2.18 option
        assertFalse(json.contains("connectionClose"));
    }

    @Test
    public void testEndpointOptions218OrNewer() {
        CamelCatalog catalog = new DefaultCamelCatalog(false);
        catalog.setVersionManager(new MavenVersionManager());
        catalog.loadVersion("2.18.3");

        String json = catalog.componentJSonSchema("ahc");
        assertNotNull(json);

        // should contain the Camel 2.18 option
        assertTrue(json.contains("connectionClose"));
    }

    @Test
    public void testRuntimeProviderLoadVersion() {
        CamelCatalog catalog = new DefaultCamelCatalog(false);
        catalog.setVersionManager(new MavenVersionManager());
        catalog.setRuntimeProvider(new DefaultRuntimeProvider());

        String version = "2.18.2";

        boolean loaded = catalog.loadVersion(version);
        assertTrue(loaded);

        loaded = catalog.loadRuntimeProviderVersion(catalog.getRuntimeProvider().getProviderGroupId(),
                catalog.getRuntimeProvider().getProviderArtifactId(), version);
        assertTrue(loaded);

        assertEquals(version, catalog.getLoadedVersion());
        assertEquals(version, catalog.getRuntimeProviderLoadedVersion());

        List<String> names = catalog.findComponentNames();

        assertTrue(names.contains("file"));
        assertTrue(names.contains("ftp"));
        assertTrue(names.contains("jms"));
    }

    @Test
    public void testRuntimeProviderLoadVersionWithCaching() {
        CamelCatalog catalog = new DefaultCamelCatalog(true);
        catalog.setVersionManager(new MavenVersionManager());
        catalog.setRuntimeProvider(new DefaultRuntimeProvider());

        String version = "2.18.2";

        boolean loaded = catalog.loadVersion(version);
        assertTrue(loaded);

        loaded = catalog.loadRuntimeProviderVersion(catalog.getRuntimeProvider().getProviderGroupId(),
                catalog.getRuntimeProvider().getProviderArtifactId(), version);
        assertTrue(loaded);

        assertEquals(version, catalog.getLoadedVersion());
        assertEquals(version, catalog.getRuntimeProviderLoadedVersion());

        List<String> names = catalog.findComponentNames();

        assertTrue(names.contains("file"));
        assertTrue(names.contains("ftp"));
        assertTrue(names.contains("jms"));
    }

    @Test
    public void testLoadUnknownVersion() {
        MavenVersionManager manager = new MavenVersionManager();
        String current = manager.getLoadedVersion();
        assertNull(current);

        // version 2.99 does not exist and cannot be loaded
        boolean loaded = manager.loadVersion("2.99");
        assertFalse(loaded);
    }

}
