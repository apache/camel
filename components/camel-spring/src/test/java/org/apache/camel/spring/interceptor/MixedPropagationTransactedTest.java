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
package org.apache.camel.spring.interceptor;

import javax.sql.DataSource;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.SpringRouteBuilder;
import org.apache.camel.spring.SpringTestSupport;
import org.apache.camel.spring.spi.TransactedRuntimeCamelException;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;

/**
 * For testing with mixed transacted propagations (required, requires new)
 */
public class MixedPropagationTransactedTest extends SpringTestSupport {

    protected SimpleJdbcTemplate jdbc;
    protected boolean useTransactionErrorHandler = true;

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
            "/org/apache/camel/spring/interceptor/mixedPropagationTransactedTest.xml");
    }

    protected int getExpectedRouteCount() {
        return 0;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // create database and insert dummy data
        final DataSource ds = getMandatoryBean(DataSource.class, "dataSource");
        jdbc = new SimpleJdbcTemplate(ds);
        jdbc.getJdbcOperations().execute("create table books (title varchar(50))");
        jdbc.update("insert into books (title) values (?)", "Camel in Action");
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        jdbc.getJdbcOperations().execute("drop table books");
    }

    public void testRequiredOnly() throws Exception {
        template.sendBody("direct:required", "Tiger in Action");

        int count = jdbc.queryForInt("select count(*) from books");
        assertEquals(1, jdbc.queryForInt("select count(*) from books where title = ?", "Tiger in Action"));
        assertEquals("Number of books", 2, count);
    }

    public void testRequired2Only() throws Exception {
        template.sendBody("direct:required2", "Tiger in Action");

        int count = jdbc.queryForInt("select count(*) from books");
        // we do 2x the book service so we should get 2 tiger books
        assertEquals(2, jdbc.queryForInt("select count(*) from books where title = ?", "Tiger in Action"));
        assertEquals("Number of books", 3, count);
    }

    public void testRequiresNewOnly() throws Exception {
        template.sendBody("direct:new", "Elephant in Action");

        int count = jdbc.queryForInt("select count(*) from books");
        assertEquals(1, jdbc.queryForInt("select count(*) from books where title = ?", "Elephant in Action"));
        assertEquals("Number of books", 2, count);
    }

    public void testRequiredAndRequiresNew() throws Exception {
        template.sendBody("direct:requiredAndNew", "Tiger in Action");

        int count = jdbc.queryForInt("select count(*) from books");
        assertEquals(2, jdbc.queryForInt("select count(*) from books where title = ?", "Tiger in Action"));
        assertEquals("Number of books", 3, count);
    }

    public void testRequiredOnlkyRollback() throws Exception {
        try {
            template.sendBody("direct:required", "Donkey in Action");
        } catch (RuntimeCamelException e) {
            // expeced as we fail
            assertIsInstanceOf(TransactedRuntimeCamelException.class, e.getCause());
            assertTrue(e.getCause().getCause() instanceof IllegalArgumentException);
            assertEquals("We don't have Donkeys, only Camels", e.getCause().getCause().getMessage());
        }

        int count = jdbc.queryForInt("select count(*) from books");
        assertEquals(0, jdbc.queryForInt("select count(*) from books where title = ?", "Donkey in Action"));
        assertEquals("Number of books", 1, count);
    }

    public void testRequiresNewOnlkyRollback() throws Exception {
        try {
            template.sendBody("direct:new", "Donkey in Action");
        } catch (RuntimeCamelException e) {
            // expeced as we fail
            assertIsInstanceOf(TransactedRuntimeCamelException.class, e.getCause());
            assertTrue(e.getCause().getCause() instanceof IllegalArgumentException);
            assertEquals("We don't have Donkeys, only Camels", e.getCause().getCause().getMessage());
        }

        int count = jdbc.queryForInt("select count(*) from books");
        assertEquals(0, jdbc.queryForInt("select count(*) from books where title = ?", "Donkey in Action"));
        assertEquals("Number of books", 1, count);
    }

    public void testRequiredAndNewRollback() throws Exception {
        try {
            template.sendBody("direct:new", "Tiger in Action");
        } catch (RuntimeCamelException e) {
            // expeced as we fail
            assertIsInstanceOf(TransactedRuntimeCamelException.class, e.getCause());
            assertTrue(e.getCause().getCause() instanceof IllegalArgumentException);
            assertEquals("We don't have Donkeys, only Camels", e.getCause().getCause().getMessage());
        }

        int count = jdbc.queryForInt("select count(*) from books");
        assertEquals(1, jdbc.queryForInt("select count(*) from books where title = ?", "Tiger in Action"));
        assertEquals(0, jdbc.queryForInt("select count(*) from books where title = ?", "Donkey in Action"));
        // the tiger in action should be committed, but our 2nd route should rollback
        assertEquals("Number of books", 2, count);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                from("direct:required")
                    .transacted("PROPATATION_REQUIRED")
                    .beanRef("bookService");

                from("direct:required2")
                    .transacted("PROPATATION_REQUIRED")
                    .beanRef("bookService")
                    .beanRef("bookService");

                from("direct:new")
                    .transacted("PROPAGATION_REQUIRES_NEW")
                    .beanRef("bookService");

                from("direct:requiredAndNew").to("direct:required", "direct:new");

                from("direct:requiredAndNewRollback")
                    .to("direct:required")
                    // change to donkey so it will rollback
                    .setBody(constant("Donkey in Action"))
                    .to("direct:new");
            }
        };
    }

}