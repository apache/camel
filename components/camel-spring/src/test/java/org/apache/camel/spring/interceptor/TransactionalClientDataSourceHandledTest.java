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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringRouteBuilder;
import org.junit.Test;

/**
 * Unit test to demonstrate the transactional client pattern.
 */
public class TransactionalClientDataSourceHandledTest extends TransactionalClientDataSourceWithOnExceptionTest {

    @Override
    @Test
    public void testTransactionRollback() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:error");
        mock.expectedMessageCount(1);

        template.sendBody("direct:fail", "Hello World");

        assertMockEndpointsSatisfied();

        int count = jdbc.queryForObject("select count(*) from books", Integer.class);
        // there should be 2 books as the first insert operation succeeded
        assertEquals("Number of books", 2, count);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1

                // on exception is also supported
                // so if an IllegalArgumentException is thrown then we route it to the mock:error
                // since we mark it as handled then the exchange will NOT rollback
                onException(IllegalArgumentException.class).handled(true).to("mock:error");

                from("direct:okay")
                    // mark this route as transacted
                    .transacted()
                    .setBody(constant("Tiger in Action")).bean("bookService")
                    .setBody(constant("Elephant in Action")).bean("bookService");

                from("direct:fail")
                    // mark this route as transacted
                    .transacted()
                    .setBody(constant("Tiger in Action")).bean("bookService")
                    .setBody(constant("Donkey in Action")).bean("bookService");
                // END SNIPPET: e1
            }
        };
    }

}