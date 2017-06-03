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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.SpringRouteBuilder;
import org.apache.camel.spring.spi.SpringTransactionPolicy;

/**
 * Unit test to demonstrate the transactional client pattern.
 */
public class TransactionalClientDataSourceTest extends TransactionClientDataSourceSupport {

    // START SNIPPET: e3
    public void testTransactionSuccess() throws Exception {
        template.sendBody("direct:okay", "Hello World");

        int count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals("Number of books", 3, count);
    }
    // END SNIPPET: e3

    // START SNIPPET: e4
    public void testTransactionRollback() throws Exception {
        try {
            template.sendBody("direct:fail", "Hello World");
            fail("Should have thrown exception");
        } catch (RuntimeCamelException e) {
            // expected as we fail
            assertIsInstanceOf(RuntimeCamelException.class, e.getCause());
            assertTrue(e.getCause().getCause() instanceof IllegalArgumentException);
            assertEquals("We don't have Donkeys, only Camels", e.getCause().getCause().getMessage());
        }

        int count = jdbc.queryForObject("select count(*) from books", Integer.class);
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
                // lookup the transaction policy
                SpringTransactionPolicy required = lookup("PROPAGATION_REQUIRED", SpringTransactionPolicy.class);

                // use this error handler instead of DeadLetterChannel that is the default
                // Notice: transactionErrorHandler is in SpringRouteBuilder
                if (isUseTransactionErrorHandler()) {
                    // useTransactionErrorHandler is only used for unit testing to reuse code
                    // for doing a 2nd test without this transaction error handler, so ignore
                    // this. For spring based transaction, end users are encouraged to use the
                    // transaction error handler instead of the default DeadLetterChannel.
                    errorHandler(transactionErrorHandler(required));
                }
                // END SNIPPET: e1

                // START SNIPPET: e2
                // set the required policy for this route
                from("direct:okay").policy(required).
                    setBody(constant("Tiger in Action")).bean("bookService").
                    setBody(constant("Elephant in Action")).bean("bookService");

                // set the required policy for this route
                from("direct:fail").policy(required).
                    setBody(constant("Tiger in Action")).bean("bookService").
                    setBody(constant("Donkey in Action")).bean("bookService");
                // END SNIPPET: e2
            }
        };
    }

}
