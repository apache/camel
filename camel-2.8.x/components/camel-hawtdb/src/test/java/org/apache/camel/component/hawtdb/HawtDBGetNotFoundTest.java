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

public class HawtDBGetNotFoundTest extends CamelTestSupport {

    private HawtDBFile hawtDBFile;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        deleteDirectory("target/data");
        File file = new File("target/data/hawtdb.dat");
        hawtDBFile = new HawtDBFile();
        hawtDBFile.setFile(file);
        hawtDBFile.start();
    }

    @Override
    public void tearDown() throws Exception {
        hawtDBFile.stop();
        super.tearDown();
    }

    @Test
    public void testGetNotFound() {
        HawtDBAggregationRepository repo = new HawtDBAggregationRepository();
        repo.setHawtDBFile(hawtDBFile);
        repo.setRepositoryName("repo1");

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello World");

        Exchange out = repo.get(context, exchange.getExchangeId());
        assertNull("Should not find exchange", out);
    }

    @Test
    public void testPutAndGetNotFound() {
        HawtDBAggregationRepository repo = new HawtDBAggregationRepository();
        repo.setHawtDBFile(hawtDBFile);
        repo.setRepositoryName("repo1");

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello World");
        log.info("Created " + exchange.getExchangeId());

        repo.add(context, exchange.getExchangeId(), exchange);
        Exchange out = repo.get(context, exchange.getExchangeId());
        assertNotNull("Should find exchange", out);

        Exchange exchange2 = new DefaultExchange(context);
        exchange2.getIn().setBody("Bye World");
        log.info("Created " + exchange2.getExchangeId());

        Exchange out2 = repo.get(context, exchange2.getExchangeId());
        assertNull("Should not find exchange", out2);
    }

}