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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringRouteBuilder;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.apache.camel.spring.spi.TransactedRuntimeCamelException;

/**
 * Unit test to demonstrate the transactional client pattern.
 */
public class TransactionalClientDataSourceWithOnExceptionTest extends TransactionalClientDataSourceTest {

    protected int getExpectedRouteCount() {
        return 0;
    }

    public void testTransactionRollback() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:error");
        mock.expectedMessageCount(1);

        try {
            template.sendBody("direct:fail", "Hello World");
        } catch (RuntimeCamelException e) {
            // expeced as we fail
            assertIsInstanceOf(TransactedRuntimeCamelException.class, e.getCause());
            assertTrue(e.getCause().getCause() instanceof IllegalArgumentException);
            assertEquals("We don't have Donkeys, only Camels", e.getCause().getCause().getMessage());
        }

        assertMockEndpointsSatisfied();

        int count = jdbc.queryForInt("select count(*) from books");
        assertEquals("Number of books", 1, count);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                // use required as transaction policy
                SpringTransactionPolicy required = bean("PROPAGATION_REQUIRED", SpringTransactionPolicy.class);

                // configure to use transaction error handler and pass on the required as it will fetch
                // the transaction manager from it that it needs
                errorHandler(transactionErrorHandler(required));

                // on exception is also supported
                onException(IllegalArgumentException.class).handled(false).to("mock:error");

                from("direct:okay")
                    .policy(required)
                    .setBody(constant("Tiger in Action")).beanRef("bookService")
                    .setBody(constant("Elephant in Action")).beanRef("bookService");

                from("direct:fail")
                    .policy(required)
                    .setBody(constant("Tiger in Action")).beanRef("bookService")
                    .setBody(constant("Donkey in Action")).beanRef("bookService");
            }
        };
    }

}
