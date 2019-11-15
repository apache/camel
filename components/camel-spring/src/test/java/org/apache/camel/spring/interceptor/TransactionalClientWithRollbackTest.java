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
package org.apache.camel.spring.interceptor;

import javax.sql.DataSource;

import org.apache.camel.RollbackExchangeException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.SpringRouteBuilder;
import org.apache.camel.spring.SpringTestSupport;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Transactional client test with rollback in the DSL.
 */
public class TransactionalClientWithRollbackTest extends SpringTestSupport {

    protected JdbcTemplate jdbc;
    protected boolean useTransactionErrorHandler = true;

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("/org/apache/camel/spring/interceptor/transactionalClientDataSource.xml");
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        final DataSource ds = getMandatoryBean(DataSource.class, "dataSource");
        jdbc = new JdbcTemplate(ds);
    }

    @Test
    public void testTransactionSuccess() throws Exception {
        template.sendBody("direct:okay", "Hello World");

        int count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals("Number of books", 3, count);
    }

    @Test
    public void testTransactionRollback() throws Exception {
        try {
            template.sendBody("direct:fail", "Hello World");
            fail("Should have thrown a RollbackExchangeException");
        } catch (RuntimeCamelException e) {
            assertIsInstanceOf(RuntimeCamelException.class, e.getCause());
            assertTrue(e.getCause().getCause() instanceof RollbackExchangeException);
        }

        int count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals("Number of books", 1, count);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        // Notice that we use the SpringRouteBuilder that has a few more features than
        // the standard RouteBuilder
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                // setup the transaction policy
                SpringTransactionPolicy required = lookup("PROPAGATION_REQUIRED", SpringTransactionPolicy.class);

                // use transaction error handler
                errorHandler(transactionErrorHandler(required));

                // must setup policy for each route
                from("direct:okay").policy(required)
                        .setBody(constant("Tiger in Action")).bean("bookService")
                        .setBody(constant("Elephant in Action")).bean("bookService");

                // must setup policy for each route
                from("direct:fail").policy(required)
                        .setBody(constant("Tiger in Action")).bean("bookService")
                        // force a rollback
                        .rollback();
            }
        };
    }

}
