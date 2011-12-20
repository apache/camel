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
package org.apache.camel.processor.onexception;

import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test inspired by user forum.
 */
public class OnExceptionSubRouteTest extends OnExceptionRouteTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                // default should errors go to mock:error
                errorHandler(deadLetterChannel("mock:error"));

                // here we start the routing with the consumer
                from("direct:start")

                    // if a MyTechnicalException is thrown we will not try to redeliver and we mark it as handled
                    // so the caller does not get a failure
                    // since we have no to then the exchange will continue to be routed to the normal error handler
                    // destination that is mock:error as defined above
                    // we MUST use .end() to indicate that this sub block is ended
                    .onException(MyTechnicalException.class).maximumRedeliveries(0).handled(true).end()

                    // if a MyFunctionalException is thrown we do not want Camel to redelivery but handle it our self using
                    // our bean myOwnHandler, then the exchange is not routed to the default error (mock:error)
                    // we MUST use .end() to indicate that this sub block is ended
                    .onException(MyFunctionalException.class).maximumRedeliveries(0).handled(true).to("bean:myOwnHandler").end()

                    // here we have the regular routing
                    .choice()
                        .when().xpath("//type = 'myType'").to("bean:myServiceBean")
                    .end()
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }


}