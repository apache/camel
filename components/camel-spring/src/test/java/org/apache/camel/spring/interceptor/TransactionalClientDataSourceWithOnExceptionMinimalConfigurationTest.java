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

/**
 * Unit test to demonstrate the transactional client pattern.
 */
public class TransactionalClientDataSourceWithOnExceptionMinimalConfigurationTest extends TransactionalClientDataSourceWithOnExceptionTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1

                // on exception is also supported
                // so if an IllegalArgumentException is thrown then we route it to the mock:error
                // since we have handled = false then the exception is not handled and Camel will
                // rollback
                onException(IllegalArgumentException.class).handled(false).to("mock:error");

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