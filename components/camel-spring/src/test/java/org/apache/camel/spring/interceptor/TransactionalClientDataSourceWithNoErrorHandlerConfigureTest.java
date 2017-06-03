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

/**
 * Easier transaction configuration as we do not have to setup a transaction error handler
 */
public class TransactionalClientDataSourceWithNoErrorHandlerConfigureTest extends TransactionalClientDataSourceTest {

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // set the required policy for this route to indicate its transactional
                from("direct:okay").policy("PROPAGATION_REQUIRED").
                    setBody(constant("Tiger in Action")).bean("bookService").
                    setBody(constant("Elephant in Action")).bean("bookService");

                // set the required policy for this route to indicate its transactional
                from("direct:fail").policy("PROPAGATION_REQUIRED").
                    setBody(constant("Tiger in Action")).bean("bookService").
                    setBody(constant("Donkey in Action")).bean("bookService");
                // END SNIPPET: e1
            }
        };
    }

}