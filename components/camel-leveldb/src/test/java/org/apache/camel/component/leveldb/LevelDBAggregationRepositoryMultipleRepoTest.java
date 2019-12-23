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

public class LevelDBAggregationRepositoryMultipleRepoTest extends CamelTestSupport {

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
    public void testMultipeRepo() {
        LevelDBAggregationRepository repo1 = new LevelDBAggregationRepository();
        repo1.setLevelDBFile(levelDBFile);
        repo1.setRepositoryName("repo1");
        repo1.setReturnOldExchange(true);

        LevelDBAggregationRepository repo2 = new LevelDBAggregationRepository();
        repo2.setLevelDBFile(levelDBFile);
        repo2.setRepositoryName("repo2");
        repo2.setReturnOldExchange(true);

        // Can't get something we have not put in...
        Exchange actual = repo1.get(context, "missing");
        assertEquals(null, actual);

        actual = repo2.get(context, "missing");
        assertEquals(null, actual);

        // Store it..
        Exchange exchange1 = new DefaultExchange(context);
        exchange1.getIn().setBody("counter:1");
        actual = repo1.add(context, "foo", exchange1);
        assertEquals(null, actual);

        // Get it back..
        actual = repo1.get(context, "foo");
        assertEquals("counter:1", actual.getIn().getBody());
        assertEquals(null, repo2.get(context, "foo"));

        // Change it..
        Exchange exchange2 = new DefaultExchange(context);
        exchange2.getIn().setBody("counter:2");
        actual = repo1.add(context, "foo", exchange2);
        // the old one
        assertEquals("counter:1", actual.getIn().getBody());

        // add to repo2
        Exchange exchange3 = new DefaultExchange(context);
        exchange3.getIn().setBody("Hello World");
        actual = repo2.add(context, "bar", exchange3);
        assertEquals(null, actual);
        assertEquals(null, repo1.get(context, "bar"));

        // Get it back..
        actual = repo1.get(context, "foo");
        assertEquals("counter:2", actual.getIn().getBody());
        assertEquals(null, repo2.get(context, "foo"));

        actual = repo2.get(context, "bar");
        assertEquals("Hello World", actual.getIn().getBody());
        assertEquals(null, repo1.get(context, "bar"));
    }

    @Test
    public void testMultipeRepoSameKeyDifferentContent() {
        LevelDBAggregationRepository repo1 = new LevelDBAggregationRepository();
        repo1.setLevelDBFile(levelDBFile);
        repo1.setRepositoryName("repo1");

        LevelDBAggregationRepository repo2 = new LevelDBAggregationRepository();
        repo2.setLevelDBFile(levelDBFile);
        repo2.setRepositoryName("repo2");

        Exchange exchange1 = new DefaultExchange(context);
        exchange1.getIn().setBody("Hello World");
        repo1.add(context, "foo", exchange1);

        Exchange exchange2 = new DefaultExchange(context);
        exchange2.getIn().setBody("Bye World");
        repo2.add(context, "foo", exchange2);

        Exchange actual = repo1.get(context, "foo");
        assertEquals("Hello World", actual.getIn().getBody());
        actual = repo2.get(context, "foo");
        assertEquals("Bye World", actual.getIn().getBody());
    }

}
