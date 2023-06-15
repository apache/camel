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

import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Cannot run on CI servers so run manually")
public class MavenArtifactProviderManualTest {

    @Test
    public void testAddComponent() {
        CamelCatalog camelCatalog = new DefaultCamelCatalog();
        MavenArtifactProvider provider = new DefaultMavenArtifactProvider();
        provider.setCacheDirectory("target/cache");

        int before = camelCatalog.findComponentNames().size();

        Set<String> names = provider.addArtifactToCatalog(camelCatalog, "org.apache.camel", "dummy-component",
                camelCatalog.getCatalogVersion());
        assertTrue(names.contains("dummy"));

        int after = camelCatalog.findComponentNames().size();

        assertEquals(1, after - before, "Should find 1 new component");
    }

}
