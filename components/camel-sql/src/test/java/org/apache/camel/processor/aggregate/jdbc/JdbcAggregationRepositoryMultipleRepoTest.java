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
package org.apache.camel.processor.aggregate.jdbc;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class JdbcAggregationRepositoryMultipleRepoTest extends CamelSpringTestSupport {

    @Test
    public void testMultipeRepo() {
        JdbcAggregationRepository repo1 = applicationContext.getBean("repo1", JdbcAggregationRepository.class);
        repo1.setReturnOldExchange(true);

        JdbcAggregationRepository repo2 = applicationContext.getBean("repo2", JdbcAggregationRepository.class);
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
        JdbcAggregationRepository repo1 = applicationContext.getBean("repo1", JdbcAggregationRepository.class);

        JdbcAggregationRepository repo2 = applicationContext.getBean("repo2", JdbcAggregationRepository.class);

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
        return new ClassPathXmlApplicationContext("org/apache/camel/processor/aggregate/jdbc/JdbcSpringDataSource.xml");
    }

}