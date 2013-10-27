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
package org.apache.camel.spring.issues;

import javax.sql.DataSource;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @version 
 */
public class SpringTransactionErrorHandlerAndContextScopedOnExceptionIssueTest extends SpringTestSupport {
    protected JdbcTemplate jdbc;

    protected void setUp() throws Exception {
        super.setUp();

        // create database and insert dummy data
        final DataSource ds = getMandatoryBean(DataSource.class, "dataSource");
        jdbc = new JdbcTemplate(ds);
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/issues/SpringTransactionErrorHandlerAndContextScopedOnExceptionIssueTest.xml");
    }

    public void testSpringTXOnExceptionIssueCommit() throws Exception {
        int count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals("Number of books", 1, count);

        // we succeeded so no message to on exception
        getMockEndpoint("mock:onException").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Lion in Action");

        assertMockEndpointsSatisfied();

        // we did commit so there should be 2 books
        count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals("Number of books", 2, count);
    }

    public void testSpringTXOnExceptionIssueRollback() throws Exception {
        int count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals("Number of books", 1, count);

        getMockEndpoint("mock:onException").expectedMessageCount(1);
        // we failed so no message to result
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Donkey in Action");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(RuntimeCamelException.class, e.getCause());
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause().getCause());
            assertEquals("We don't have Donkeys, only Camels", e.getCause().getCause().getMessage());
        }

        assertMockEndpointsSatisfied();

        // we did rollback so there should be 1 books
        count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals("Number of books", 1, count);
    }

}
