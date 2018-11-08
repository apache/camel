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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringRouteBuilder;
import org.junit.Test;

/**
 * Easier transaction configuration as we do not have to setup a transaction error handler
 */
public class TransactedInterceptSendToEndpointTest extends TransactionalClientDataSourceTest {

    @Test
    public void testTransactionSuccess() throws Exception {
        MockEndpoint intercepted = getMockEndpoint("mock:intercepted");
        intercepted.expectedBodiesReceived("Hello World");

        super.testTransactionSuccess();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTransactionRollback() throws Exception {
        MockEndpoint intercepted = getMockEndpoint("mock:intercepted");
        intercepted.expectedBodiesReceived("Tiger in Action");

        super.testTransactionRollback();

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                interceptSendToEndpoint("direct:(foo|bar)").to("mock:intercepted");

                from("direct:okay")
                    .transacted()
                    .to("direct:foo")
                    .setBody(constant("Tiger in Action")).bean("bookService")
                    .setBody(constant("Elephant in Action")).bean("bookService");

                from("direct:fail")
                    .transacted()
                    .setBody(constant("Tiger in Action")).bean("bookService")
                    .to("direct:bar")
                    .setBody(constant("Donkey in Action")).bean("bookService");

                from("direct:foo").to("log:okay");

                from("direct:bar").to("mock:fail");
            }
        };
    }

}