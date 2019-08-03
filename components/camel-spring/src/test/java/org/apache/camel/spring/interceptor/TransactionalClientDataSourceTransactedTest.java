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
public class TransactionalClientDataSourceTransactedTest extends TransactionalClientDataSourceTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:okay")
                    // marks this route as transacted, and we dont pass in any parameters so we
                    // will auto lookup and use the Policy defined in the spring XML file
                    .transacted()
                    .setBody(constant("Tiger in Action")).bean("bookService")
                    .setBody(constant("Elephant in Action")).bean("bookService");

                // marks this route as transacted that will use the single policy defined in the registry
                from("direct:fail")
                    // marks this route as transacted, and we dont pass in any parameters so we
                    // will auto lookup and use the Policy defined in the spring XML file
                    .transacted()
                    .setBody(constant("Tiger in Action")).bean("bookService")
                    .setBody(constant("Donkey in Action")).bean("bookService");
                // END SNIPPET: e1
            }
        };
    }

}
