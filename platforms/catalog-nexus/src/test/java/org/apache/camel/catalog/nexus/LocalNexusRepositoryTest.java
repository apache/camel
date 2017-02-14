/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.catalog.nexus;

import junit.framework.TestCase;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.Ignore;
import org.junit.Test;

public class LocalNexusRepositoryTest extends TestCase {

    private LocalFileNexusRepository repo = new LocalFileNexusRepository();
    private CamelCatalog catalog = new DefaultCamelCatalog();

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        repo.setCamelCatalog(catalog);
        repo.setInitialDelay(1);
        repo.setNexusUrl("dummy");
    }

    @Test
    @Ignore("Work in progress")
    public void testLocalNexus() throws Exception {
        int before = catalog.findComponentNames().size();

        repo.start();

        // TODO: create custom component we can use for testing here
        // and only wait as long until a new component is added
        Thread.sleep(5000);

        repo.stop();

        int after = catalog.findComponentNames().size();

        assertTrue("There should be 1 component found", after - before == 1);
    }
}
