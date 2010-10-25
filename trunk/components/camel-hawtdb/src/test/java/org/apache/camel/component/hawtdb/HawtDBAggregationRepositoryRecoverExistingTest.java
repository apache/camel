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
package org.apache.camel.component.hawtdb;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class HawtDBAggregationRepositoryRecoverExistingTest extends CamelTestSupport {

    private HawtDBFile hawtDBFile;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        deleteDirectory("target/data");
        File file = new File("target/data/hawtdb.dat");
        hawtDBFile = new HawtDBFile();
        hawtDBFile.setFile(file);
    }

    @Test
    public void testExisting() throws Exception {
        HawtDBAggregationRepository repo = new HawtDBAggregationRepository();
        repo.setHawtDBFile(hawtDBFile);
        repo.setRepositoryName("repo1");
        repo.setReturnOldExchange(true);
        repo.setUseRecovery(true);
        repo.start();

        // Store it..
        Exchange exchange1 = new DefaultExchange(context);
        exchange1.getIn().setBody("counter:1");
        Exchange actual = repo.add(context, "foo", exchange1);
        assertEquals(null, actual);

        // Remove it, which makes it in the pre confirm stage
        repo.remove(context, "foo", exchange1);

        String id = exchange1.getExchangeId();

        // stop the repo
        repo.stop();

        Thread.sleep(1000);

        // load the repo again
        repo.start();

        // Get it back..
        actual = repo.get(context, "foo");
        assertNull(actual);

        // Recover it
        actual = repo.recover(context, id);
        assertNotNull(actual);
        assertEquals("counter:1", actual.getIn().getBody());

        repo.stop();
    }

}