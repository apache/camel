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

import java.io.InputStream;
import java.util.List;

import junit.framework.TestCase;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.CatalogHelper;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.karaf.KarafRuntimeProvider;
import org.apache.camel.catalog.springboot.SpringBootRuntimeProvider;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Cannot run on CI servers so run manually")
public class MavenVersionManagerTest extends TestCase {

    private static final String COMPONENTS_CATALOG = "org/apache/camel/catalog/components.properties";

    @Test
    public void testLoadVersion() throws Exception {
        MavenVersionManager manager = new MavenVersionManager();
        String current = manager.getLoadedVersion();
        assertNull(current);

        boolean loaded = manager.loadVersion("2.17.1");
        assertTrue(loaded);

        assertEquals("2.17.1", manager.getLoadedVersion());

        InputStream is = manager.getResourceAsStream(COMPONENTS_CATALOG);
        assertNotNull(is);
        String text = CatalogHelper.loadText(is);

        // should not contain Camel 2.18 components
        assertFalse(text.contains("servicenow"));
        // but 2.17 components such
        assertTrue(text.contains("nats"));
    }

    @Test
    public void testEndpointOptions217() throws Exception {
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
    public void testEndpointOptions218OrNewer() throws Exception {
        CamelCatalog catalog = new DefaultCamelCatalog(false);

        String json = catalog.componentJSonSchema("ahc");
        assertNotNull(json);

        // should contain the Camel 2.18 option
        assertTrue(json.contains("connectionClose"));
    }

    @Test
    public void testRuntimeProviderLoadVersion() throws Exception {
        CamelCatalog catalog = new DefaultCamelCatalog(false);
        catalog.setVersionManager(new MavenVersionManager());
        catalog.setRuntimeProvider(new SpringBootRuntimeProvider());

        String version = "2.18.2";

        boolean loaded = catalog.loadVersion(version);
        assertTrue(loaded);

        loaded = catalog.loadRuntimeProviderVersion(catalog.getRuntimeProvider().getProviderGroupId(), catalog.getRuntimeProvider().getProviderArtifactId(), version);
        assertTrue(loaded);

        assertEquals(version, catalog.getLoadedVersion());
        assertEquals(version, catalog.getRuntimeProviderLoadedVersion());

        List<String> names = catalog.findComponentNames();

        assertTrue(names.contains("file"));
        assertTrue(names.contains("ftp"));
        assertTrue(names.contains("jms"));
        // camel-ejb does not work in spring-boot
        assertFalse(names.contains("ejb"));
        // camel-pax-logging does not work in spring-boot
        assertFalse(names.contains("paxlogging"));
    }

    @Test
    public void testRuntimeProviderLoadVersionWithCaching() throws Exception {
        CamelCatalog catalog = new DefaultCamelCatalog(true);
        catalog.setVersionManager(new MavenVersionManager());
        catalog.setRuntimeProvider(new SpringBootRuntimeProvider());

        String version = "2.18.2";

        boolean loaded = catalog.loadVersion(version);
        assertTrue(loaded);

        loaded = catalog.loadRuntimeProviderVersion(catalog.getRuntimeProvider().getProviderGroupId(), catalog.getRuntimeProvider().getProviderArtifactId(), version);
        assertTrue(loaded);

        assertEquals(version, catalog.getLoadedVersion());
        assertEquals(version, catalog.getRuntimeProviderLoadedVersion());

        List<String> names = catalog.findComponentNames();

        assertTrue(names.contains("file"));
        assertTrue(names.contains("ftp"));
        assertTrue(names.contains("jms"));
        // camel-ejb does not work in spring-boot
        assertFalse(names.contains("ejb"));
        // camel-pax-logging does not work in spring-boot
        assertFalse(names.contains("paxlogging"));
    }

    @Test
    public void testCatalogKarafRuntimeProviderVersionSwitch() throws Exception {
        CamelCatalog catalog = new DefaultCamelCatalog(true);
        MavenVersionManager mvm = new MavenVersionManager();
        mvm.addMavenRepository("asf-ga", "https://repo.maven.apache.org/maven2");
        mvm.addMavenRepository("asf-snapshots", "https://repository.apache.org/content/groups/snapshots");
        catalog.setVersionManager(mvm);
        catalog.setRuntimeProvider(new KarafRuntimeProvider());

        boolean loaded = catalog.loadVersion("2.18.1");
        assertTrue("Unable to load Camel Catalog 2.18.1", loaded);
        loaded = catalog.loadRuntimeProviderVersion("org.apache.camel", "camel-catalog-provider-karaf", "2.18.1");
        assertTrue("Unable to load Karaf Provider Camel Catalog 2.18.1", loaded);
        int components = catalog.findComponentNames().size();
        System.out.println("2.18.1 has " + components + " components");
        assertFalse("Should not have ejb component", catalog.findComponentNames().contains("ejb"));

        loaded = catalog.loadVersion("2.19.1");
        assertTrue("Unable to switch to Camel Catalog 2.19.1", loaded);
        loaded = catalog.loadRuntimeProviderVersion("org.apache.camel", "camel-catalog-provider-karaf", "2.19.1");
        assertTrue("Unable to load Karaf Provider Camel Catalog 2.19.1", loaded);
        int componentsNewer = catalog.findComponentNames().size();
        assertTrue("Both catalog versions shouldn't have the same count of components.", components != componentsNewer);
        System.out.println("2.19.1 has " + componentsNewer + " components");
        assertFalse("Should not have ejb component", catalog.findComponentNames().contains("ejb"));

        loaded = catalog.loadVersion("2.18.1");
        assertTrue("Unable to load Camel Catalog 2.18.1", loaded);
        loaded = catalog.loadRuntimeProviderVersion("org.apache.camel", "camel-catalog-provider-karaf", "2.18.1");
        assertTrue("Unable to load Karaf Provider Camel Catalog 2.18.1", loaded);
        int components3 = catalog.findComponentNames().size();
        assertTrue("Newer load does not match older one", components == components3);
        assertFalse("Should not have ejb component", catalog.findComponentNames().contains("ejb"));

        System.out.println("2.18.1 has " + components3 + " components");
    }

    @Test
    public void testLoadUnknownVersion() throws Exception {
        MavenVersionManager manager = new MavenVersionManager();
        String current = manager.getLoadedVersion();
        assertNull(current);

        // version 2.99 does not exists and cannot be loaded
        boolean loaded = manager.loadVersion("2.99");
        assertFalse(loaded);
    }

}
