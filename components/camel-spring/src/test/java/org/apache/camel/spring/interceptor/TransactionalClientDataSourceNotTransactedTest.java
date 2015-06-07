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

/**
 * Same route but not transacted
 */
public class TransactionalClientDataSourceNotTransactedTest extends TransactionalClientDataSourceTest {

    public void testTransactionRollback() throws Exception {
        try {
            template.sendBody("direct:fail", "Hello World");
            fail("Should have thrown exception");
        } catch (RuntimeCamelException e) {
            // expected as we fail
            assertTrue(e.getCause() instanceof IllegalArgumentException);
            assertEquals("We don't have Donkeys, only Camels", e.getCause().getMessage());
        }

        int count = jdbc.queryForObject("select count(*) from books", Integer.class);
        // should get 2 books as the first operation will succeed and we are not transacted
        assertEquals("Number of books", 2, count);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                from("direct:okay")
                    .setBody(constant("Tiger in Action")).bean("bookService")
                    .setBody(constant("Elephant in Action")).bean("bookService");

                from("direct:fail")
                    .setBody(constant("Tiger in Action")).bean("bookService")
                    .setBody(constant("Donkey in Action")).bean("bookService");
            }
        };
    }

}