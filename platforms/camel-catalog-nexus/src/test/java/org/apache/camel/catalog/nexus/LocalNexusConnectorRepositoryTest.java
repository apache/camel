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
import org.apache.camel.catalog.connector.CamelConnectorCatalog;
import org.apache.camel.catalog.connector.DefaultCamelConnectorCatalog;
import org.junit.Test;

public class LocalNexusConnectorRepositoryTest extends TestCase {

    private CamelConnectorCatalog catalog = new DefaultCamelConnectorCatalog();

    @Test
    public void testLocalNexus() throws Exception {
        LocalFileConnectorNexusRepository repo = new LocalFileConnectorNexusRepository();
        repo.setInitialDelay(2);
        repo.setDelay(3);
        repo.setNexusUrl("dummy");
        repo.setCamelConnectorCatalog(catalog);

        final CountDownLatch latch = new CountDownLatch(1);
        repo.setOnAddConnector(latch::countDown);

        int before = catalog.findConnector(false).size();

        repo.start();

        assertTrue("Should have found connector", latch.await(10, TimeUnit.SECONDS));

        repo.stop();

        int after = catalog.findConnector(false).size();

        assertTrue("There should be 1 connector found", after - before == 1);
    }

}
