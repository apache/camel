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
 * Easier transaction configuration as we do not have to setup a transaction error handler
 */
public class TransactionalClientDataSourceTransactedWithChoiceRedeliveryTest extends TransactionalClientDataSourceTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                // will try at most 3 times
                errorHandler(transactionErrorHandler().maximumRedeliveries(3));

                from("direct:okay")
                    .transacted()
                    .choice()
                        .when(body().contains("Hello")).to("log:hello")
                    .otherwise()
                        .to("log:other")
                    .end()
                    .to("direct:tiger")
                    .setBody(constant("Elephant in Action")).bean("bookService");

                from("direct:tiger")
                    .transacted()
                    .setBody(constant("Tiger in Action")).bean("bookService");

                from("direct:donkey")
                    // notice this one is not marked as transacted but since the exchange is transacted
                    // the default error handler will not handle it and thus not interfeer
                    .setBody(constant("Donkey in Action")).bean("bookService");

                // marks this route as transacted that will use the single policy defined in the registry
                from("direct:fail")
                    .transacted()
                    .setBody(constant("Tiger in Action")).bean("bookService")
                    .to("direct:donkey");
            }
        };
    }

}