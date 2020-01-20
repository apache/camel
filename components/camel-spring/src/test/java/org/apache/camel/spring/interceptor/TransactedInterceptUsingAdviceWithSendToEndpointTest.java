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

import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.reifier.RouteReifier;
import org.junit.Test;

/**
 * Easier transaction configuration as we do not have to setup a transaction error handler
 */
public class TransactedInterceptUsingAdviceWithSendToEndpointTest extends TransactionalClientDataSourceTest {

    @Override
    @Test
    public void testTransactionSuccess() throws Exception {
        MockEndpoint intercepted = getMockEndpoint("mock:intercepted");
        
        addInterceptor("ok_route");
        
        intercepted.expectedBodiesReceived("Hello World");

        super.testTransactionSuccess();

        assertMockEndpointsSatisfied();
    }

    @Override
    @Test
    public void testTransactionRollback() throws Exception {
        MockEndpoint intercepted = getMockEndpoint("mock:intercepted");
        
        addInterceptor("fail_route");
        
        intercepted.expectedBodiesReceived("Tiger in Action");

        super.testTransactionRollback();

        assertMockEndpointsSatisfied();
    }
    
    private void addInterceptor(String routeId) throws Exception {
        RouteReifier.adviceWith(context.getRouteDefinition(routeId), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("direct:(foo|bar)")
                    .to("mock:intercepted");
            }
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:okay")
                    .routeId("ok_route")
                    .transacted()
                    .enrich("direct:foo", (oldExchange, newExchange) -> {
                        return newExchange;
                    })
                    .setBody(constant("Tiger in Action")).bean("bookService")
                    .setBody(constant("Elephant in Action")).bean("bookService");

                from("direct:fail")
                    .routeId("fail_route")
                    .transacted()
                    .setBody(constant("Tiger in Action")).bean("bookService")
                    .enrich("direct:bar", (oldExchange, newExchange) -> {
                        return newExchange;
                    })
                    .setBody(constant("Donkey in Action")).bean("bookService");

                from("direct:foo").to("log:okay");

                from("direct:bar").to("mock:fail");
            }
        };
    }

}
