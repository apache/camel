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
package org.apache.camel.processor.aggregate.jdbc;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class JdbcAggregationRepositoryMultipleRepoTest extends CamelSpringTestSupport {

    @Test
    public void testMultipeRepo() {
        JdbcAggregationRepository repo1 = applicationContext.getBean("repo1", JdbcAggregationRepository.class);
        repo1.setReturnOldExchange(true);
        repo1.start();

        JdbcAggregationRepository repo2 = applicationContext.getBean("repo2", JdbcAggregationRepository.class);
        repo2.setReturnOldExchange(true);
        repo2.start();

        // Can't get something we have not put in...
        Exchange actual = repo1.get(context, "missing");
        assertNull(actual);

        actual = repo2.get(context, "missing");
        assertNull(actual);

        // Store it..
        Exchange exchange1 = new DefaultExchange(context);
        exchange1.getIn().setBody("counter:1");
        actual = repo1.add(context, "foo", exchange1);
        assertNull(actual);

        // Get it back..
        actual = repo1.get(context, "foo");
        assertEquals("counter:1", actual.getIn().getBody());
        assertNull(repo2.get(context, "foo"));

        // Change it after reading the current exchange with version
        Exchange exchange2 = new DefaultExchange(context);
        exchange2 = repo1.get(context, "foo");
        exchange2.getIn().setBody("counter:2");
        actual = repo1.add(context, "foo", exchange2);
        // the old one
        assertEquals("counter:1", actual.getIn().getBody());

        // add to repo2
        Exchange exchange3 = new DefaultExchange(context);
        exchange3.getIn().setBody("Hello World");
        actual = repo2.add(context, "bar", exchange3);
        assertNull(actual);
        assertNull(repo1.get(context, "bar"));

        // Get it back..
        actual = repo1.get(context, "foo");
        assertEquals("counter:2", actual.getIn().getBody());
        assertNull(repo2.get(context, "foo"));

        actual = repo2.get(context, "bar");
        assertEquals("Hello World", actual.getIn().getBody());
        assertNull(repo1.get(context, "bar"));
    }

    @Test
    public void testMultipeRepoSameKeyDifferentContent() {
        JdbcAggregationRepository repo1 = applicationContext.getBean("repo1", JdbcAggregationRepository.class);
        repo1.start();

        JdbcAggregationRepository repo2 = applicationContext.getBean("repo2", JdbcAggregationRepository.class);
        repo2.start();

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

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return newAppContext("JdbcSpringDataSource.xml");
    }

}
