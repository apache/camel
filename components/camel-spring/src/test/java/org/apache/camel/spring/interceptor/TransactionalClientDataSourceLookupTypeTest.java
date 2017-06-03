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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.SpringRouteBuilder;
import org.apache.camel.spring.spi.SpringTransactionPolicy;

/**
 * Unit test to demonstrate the transactional client pattern.
 */
public class TransactionalClientDataSourceLookupTypeTest extends TransactionalClientDataSourceTest {

    protected RouteBuilder createRouteBuilder() throws Exception {
        // START SNIPPET: e1
        // Notice that we use the SpringRouteBuilder that has a few more features than
        // the standard RouteBuilder
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // lookup the transaction policy
                SpringTransactionPolicy required = lookup(SpringTransactionPolicy.class);

                // use this error handler instead of DeadLetterChannel that is the default
                // Notice: transactionErrorHandler is in SpringRouteBuilder
                if (useTransactionErrorHandler) {
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