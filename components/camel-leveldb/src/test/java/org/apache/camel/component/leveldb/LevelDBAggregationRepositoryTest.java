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
package org.apache.camel.component.leveldb;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LevelDBAggregationRepositoryTest extends CamelTestSupport {

    private LevelDBFile levelDBFile;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        deleteDirectory("target/data");
        File file = new File("target/data/leveldb.dat");
        levelDBFile = new LevelDBFile();
        levelDBFile.setFile(file);
        levelDBFile.start();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        levelDBFile.stop();
        super.tearDown();
    }

    @Test
    public void testOperations() {
        LevelDBAggregationRepository repo = new LevelDBAggregationRepository();
        repo.setLevelDBFile(levelDBFile);
        repo.setRepositoryName("repo1");
        repo.setReturnOldExchange(true);

        // Can't get something we have not put in...
        Exchange actual = repo.get(context, "missing");
        assertEquals(null, actual);

        // Store it..
        Exchange exchange1 = new DefaultExchange(context);
        exchange1.getIn().setBody("counter:1");
        actual = repo.add(context, "foo", exchange1);
        assertEquals(null, actual);

        // Get it back..
        actual = repo.get(context, "foo");
        assertEquals("counter:1", actual.getIn().getBody());

        // Change it..
        Exchange exchange2 = new DefaultExchange(context);
        exchange2.getIn().setBody("counter:2");
        actual = repo.add(context, "foo", exchange2);
        // the old one
        assertEquals("counter:1", actual.getIn().getBody());

        // Get it back..
        actual = repo.get(context, "foo");
        assertEquals("counter:2", actual.getIn().getBody());

        // now remove it
        repo.remove(context, "foo", actual);
        actual = repo.get(context, "foo");
        assertEquals(null, actual);

        // add it again
        exchange1 = new DefaultExchange(context);
        exchange1.getIn().setBody("counter:3");
        actual = repo.add(context, "foo", exchange1);
        assertEquals(null, actual);

        // Get it back..
        actual = repo.get(context, "foo");
        assertEquals("counter:3", actual.getIn().getBody());
    }

}
