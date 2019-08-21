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
package org.apache.camel.processor;

import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test to verify that handled policy is working as expected for wiki
 * documentation.
 */
public class DeadLetterChannelHandledExampleWithDefaultErrorHandlerTest extends DeadLetterChannelHandledExampleTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // we do special error handling for when OrderFailedException is
                // thrown
                onException(OrderFailedException.class)
                    // we mark the exchange as handled so the caller doesn't
                    // receive the
                    // OrderFailedException but whatever we want to return
                    // instead
                    .handled(true)
                    // this bean handles the error handling where we can
                    // customize the error
                    // response using java code
                    .bean(OrderService.class, "orderFailed")
                    // and since this is an unit test we use mocks for testing
                    .to("mock:error");

                // this is our route where we handle orders
                from("direct:start")
                    // this bean is our order service
                    .bean(OrderService.class, "handleOrder")
                    // this is the destination if the order is OK
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

}
