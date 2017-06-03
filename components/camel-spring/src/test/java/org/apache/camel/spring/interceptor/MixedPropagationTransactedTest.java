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
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * For testing with mixed transacted propagation (required, requires new)
 */
public class MixedPropagationTransactedTest extends SpringTestSupport {

    protected JdbcTemplate jdbc;
    protected boolean useTransactionErrorHandler = true;

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
            "/org/apache/camel/spring/interceptor/mixedPropagationTransactedTest.xml");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // create database and insert dummy data
        final DataSource ds = getMandatoryBean(DataSource.class, "dataSource");
        jdbc = new JdbcTemplate(ds);
    }

    public void testRequiredOnly() throws Exception {
        template.sendBody("direct:required", "Tiger in Action");

        int count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals(new Integer(1), jdbc.queryForObject("select count(*) from books where title = ?", Integer.class, "Tiger in Action"));
        assertEquals("Number of books", 2, count);
    }

    public void testRequired2Only() throws Exception {
        template.sendBody("direct:required2", "Tiger in Action");

        int count = jdbc.queryForObject("select count(*) from books", Integer.class);
        // we do 2x the book service so we should get 2 tiger books
        assertEquals(new Integer(2), jdbc.queryForObject("select count(*) from books where title = ?", Integer.class, "Tiger in Action"));
        assertEquals("Number of books", 3, count);
    }

    public void testRequiresNewOnly() throws Exception {
        template.sendBody("direct:new", "Elephant in Action");

        int count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals(new Integer(1), jdbc.queryForObject("select count(*) from books where title = ?", Integer.class, "Elephant in Action"));
        assertEquals("Number of books", 2, count);
    }

    public void testRequiredAndRequiresNew() throws Exception {
        template.sendBody("direct:requiredAndNew", "Tiger in Action");

        int count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals(new Integer(2), jdbc.queryForObject("select count(*) from books where title = ?", Integer.class, "Tiger in Action"));
        assertEquals("Number of books", 3, count);
    }

    public void testRequiredOnlyRollback() throws Exception {
        try {
            template.sendBody("direct:required", "Donkey in Action");
            fail("Should have thrown exception");
        } catch (RuntimeCamelException e) {
            // expected as we fail
            assertIsInstanceOf(RuntimeCamelException.class, e.getCause());
            assertTrue(e.getCause().getCause() instanceof IllegalArgumentException);
            assertEquals("We don't have Donkeys, only Camels", e.getCause().getCause().getMessage());
        }

        int count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals(new Integer(0), jdbc.queryForObject("select count(*) from books where title = ?", Integer.class, "Donkey in Action"));
        assertEquals("Number of books", 1, count);
    }

    public void testRequiresNewOnlyRollback() throws Exception {
        try {
            template.sendBody("direct:new", "Donkey in Action");
            fail("Should have thrown exception");
        } catch (RuntimeCamelException e) {
            // expected as we fail
            assertIsInstanceOf(RuntimeCamelException.class, e.getCause());
            assertTrue(e.getCause().getCause() instanceof IllegalArgumentException);
            assertEquals("We don't have Donkeys, only Camels", e.getCause().getCause().getMessage());
        }

        int count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals(new Integer(0), jdbc.queryForObject("select count(*) from books where title = ?", Integer.class, "Donkey in Action"));
        assertEquals("Number of books", 1, count);
    }

    public void testRequiredAndNewRollback() throws Exception {
        try {
            template.sendBody("direct:requiredAndNewRollback", "Tiger in Action");
        } catch (RuntimeCamelException e) {
            // expeced as we fail
            assertIsInstanceOf(RuntimeCamelException.class, e.getCause());
            assertTrue(e.getCause().getCause() instanceof IllegalArgumentException);
            assertEquals("We don't have Donkeys, only Camels", e.getCause().getCause().getMessage());
        }

        int count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals(new Integer(1), jdbc.queryForObject("select count(*) from books where title = ?", Integer.class, "Tiger in Action"));
        assertEquals(new Integer(0), jdbc.queryForObject("select count(*) from books where title = ?", Integer.class, "Donkey in Action"));
        // the tiger in action should be committed, but our 2nd route should rollback
        assertEquals("Number of books", 2, count);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                from("direct:required")
                    .transacted("PROPATATION_REQUIRED")
                    .bean("bookService");

                from("direct:required2")
                    .transacted("PROPATATION_REQUIRED")
                    .bean("bookService")
                    .bean("bookService");

                from("direct:new")
                    .transacted("PROPAGATION_REQUIRES_NEW")
                    .bean("bookService");

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