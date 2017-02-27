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

import junit.framework.TestCase;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.Test;

public class MavenArtifactProviderTest extends TestCase {

    @Test
    public void testAddComponent() {
        CamelCatalog camelCatalog = new DefaultCamelCatalog();
        MavenArtifactProvider provider = new DefaultMavenArtifactProvider();

        int before = camelCatalog.findComponentNames().size();

        boolean found = provider.addArtifactToCatalog(camelCatalog, "org.apache.camel", "dummy-component", camelCatalog.getCatalogVersion());
        assertTrue(found);

        int after = camelCatalog.findComponentNames().size();

        assertTrue("Should find 1 new component", after - before == 1);
    }
}
