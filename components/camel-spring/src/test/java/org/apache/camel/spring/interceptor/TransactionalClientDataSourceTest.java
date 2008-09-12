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
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unit test to demonstrate the transactional client pattern.
 */
public class TransactionalClientDataSourceTest extends SpringTestSupport {

    protected JdbcTemplate jdbc;
    protected boolean useTransactionErrorHandler = true;

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
            "/org/apache/camel/spring/interceptor/transactionalClientDataSource.xml");
    }

    protected int getExpectedRouteCount() {
        return 0;
    }

    @Override
    protected void setUp() throws Exception {
        this.disableJMX();
        super.setUp();

        // START SNIPPET: e5
        // create database and insert dummy data
        final DataSource ds = getMandatoryBean(DataSource.class, "dataSource");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table books (title varchar(50))");
        jdbc.update("insert into books (title) values (?)", new Object[] {"Camel in Action"});
        // END SNIPPET: e5
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        jdbc.execute("drop table books");
        this.enableJMX();
    }

    // START SNIPPET: e3
    public void testTransactionSuccess() throws Exception {
        template.sendBody("direct:okay", "Hello World");

        int count = jdbc.queryForInt("select count(*) from books");
        assertEquals("Number of books", 3, count);
    }
    // END SNIPPET: e3

    // START SNIPPET: e4
    public void testTransactionRollback() throws Exception {
        try {
            template.sendBody("direct:fail", "Hello World");
        } catch (RuntimeCamelException e) {
            // expeced as we fail
            assertTrue(e.getCause() instanceof IllegalArgumentException);
            assertEquals("We don't have Donkeys, only Camels", e.getCause().getMessage());
        }

        int count = jdbc.queryForInt("select count(*) from books");
        assertEquals("Number of books", 1, count);
    }
    // END SNIPPET: e4

    protected RouteBuilder createRouteBuilder() throws Exception {
        // START SNIPPET: e1
        // Notice that we use the SpringRouteBuilder that has a few more features than
        // the standard RouteBuilder
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // setup the transaction policy
                SpringTransactionPolicy required = context.getRegistry()
                    .lookup("PROPAGATION_REQUIRED", SpringTransactionPolicy.class);

                // use this error handler instead of DeadLetterChannel that is the default
                // Notice: transactionErrorHandler is in SpringRouteBuilder
                if (useTransactionErrorHandler) {
                    // useTransactionErrorHandler is only used for unit testing to reuse code
                    // for doing a 2nd test without this transaction error handler, so ignore
                    // this. For spring based transaction, end users are encouraged to use the
                    // transaction error handler instead of the default DeadLetterChannel.
                    errorHandler(transactionErrorHandler(required).
                        // notice that the builder has builder methods for chained configuration
                        delay(5 * 1000L));
                }
                // END SNIPPET: e1

                // START SNIPPET: e2
                // set the required policy for this route
                from("direct:okay").policy(required).
                    setBody(constant("Tiger in Action")).beanRef("bookService").
                    setBody(constant("Elephant in Action")).beanRef("bookService");

                // set the required policy for this route
                from("direct:fail").policy(required).
                    setBody(constant("Tiger in Action")).beanRef("bookService").
                    setBody(constant("Donkey in Action")).beanRef("bookService");
                // END SNIPPET: e2
            }
        };
    }

}
