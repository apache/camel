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
import org.apache.camel.spring.SpringRouteBuilder;
import org.junit.Test;

/**
 * Same route but not transacted
 */
public class TransactionalClientDataSourceMixedTransactedTest extends TransactionalClientDataSourceTest {

    @Override
    @Test
    public void testTransactionRollback() throws Exception {
        // through the onException clause below we've marked the exceptions containing the message
        // "Donkey" as being handled so that we don't count with any exception on the client side.
        template.sendBody("direct:fail", "Hello World");

        int count = jdbc.queryForObject("select count(*) from books", Integer.class);
        // should get 2 books as the first operation will succeed and we are not transacted
        assertEquals("Number of books", 2, count);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                // ignore failure if its something with Donkey
                onException(IllegalArgumentException.class).onWhen(exceptionMessage().contains("Donkey")).handled(true);

                from("direct:okay")
                    // mark this route as transacted
                    .transacted()
                    .setBody(constant("Tiger in Action")).bean("bookService")
                    .setBody(constant("Elephant in Action")).bean("bookService")
                    .setBody(constant("Donkey in Action")).bean("bookService");

                from("direct:fail")
                    // and this route is not transacted
                    .setBody(constant("Tiger in Action")).bean("bookService")
                    .setBody(constant("Donkey in Action")).bean("bookService");
            }
        };
    }

}