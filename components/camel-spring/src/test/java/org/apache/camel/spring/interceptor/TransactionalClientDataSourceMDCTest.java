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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.SpringRouteBuilder;
import org.slf4j.MDC;

/**
 * Easier transaction configuration as we do not have to setup a transaction error handler
 */
public class TransactionalClientDataSourceMDCTest extends TransactionalClientDataSourceTest {

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                context.setUseMDCLogging(true);

                from("direct:okay").routeId("route-a")
                    .transacted()
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                assertEquals("route-a", MDC.get("camel.routeId"));
                                assertEquals(exchange.getExchangeId(), MDC.get("camel.exchangeId"));
                                assertNotNull(MDC.get("camel.transactionKey"));
                            }
                        })
                    .to("log:foo")
                    .setBody(constant("Tiger in Action")).bean("bookService")
                    .to("log:bar")
                    .setBody(constant("Elephant in Action")).bean("bookService");

                // marks this route as transacted that will use the single policy defined in the registry
                from("direct:fail").routeId("route-b")
                    .transacted()
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                assertEquals("route-b", MDC.get("camel.routeId"));
                                assertEquals(exchange.getExchangeId(), MDC.get("camel.exchangeId"));
                                assertNotNull(MDC.get("camel.transactionKey"));
                            }
                        })
                    .to("log:foo2")
                    .setBody(constant("Tiger in Action")).bean("bookService")
                    .to("log:bar2")
                    .setBody(constant("Donkey in Action")).bean("bookService");
            }
        };
    }

}
