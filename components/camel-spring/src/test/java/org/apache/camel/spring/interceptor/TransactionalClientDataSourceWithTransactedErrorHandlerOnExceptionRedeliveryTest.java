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
 * @version 
 */
public class TransactionalClientDataSourceWithTransactedErrorHandlerOnExceptionRedeliveryTest extends TransactionalClientDataSourceRedeliveryTest {

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1

                // configure transacted error handler to use up till 4 redeliveries
                // we have not passed in any spring TX manager. Camel will automatic
                // find it in the spring application context. You only need to help
                // Camel in case you have multiple TX managers
                errorHandler(transactionErrorHandler().maximumRedeliveries(6));

                // speical for this exception we only want to do it at most 4 times
                onException(IllegalArgumentException.class).maximumRedeliveries(4);

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