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
package org.apache.camel.catalog.nexus;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.Test;

public class LocalNexusComponentCatalogRepositoryTest extends TestCase {

    private final CamelCatalog catalog = new DefaultCamelCatalog();

    @Test
    public void testLocalNexus() throws Exception {
        int before = catalog.findComponentNames().size();

        LocalFileComponentCatalogNexusRepository repo = new LocalFileComponentCatalogNexusRepository();
        repo.setCamelCatalog(catalog);
        repo.setInitialDelay(2);
        repo.setDelay(3);
        repo.setNexusUrl("dummy");

        final CountDownLatch latch = new CountDownLatch(1);
        repo.setOnAddComponent(latch::countDown);

        repo.start();

        assertTrue("Should have found component", latch.await(10, TimeUnit.SECONDS));

        repo.stop();

        int after = catalog.findComponentNames().size();

        assertTrue("There should be 1 component found", after - before == 1);
    }
}
