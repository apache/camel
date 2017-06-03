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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class OnExceptionContinueTwoTest extends ContextTestSupport {

    public void testContinuedTwo() throws Exception {
        getMockEndpoint("mock:start").expectedMessageCount(1);
        getMockEndpoint("mock:middle").expectedMessageCount(1);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        // and we should keep the exception so we know what caused the failure
        mock.message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isInstanceOf(IllegalArgumentException.class);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        Exception cause = mock.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertEquals("Forced Again", cause.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // tell Camel to handle and continue when this exception is thrown
                onException(IllegalArgumentException.class).continued(true);

                from("direct:start")
                    .to("mock:start")
                    .throwException(new IllegalArgumentException("Forced"))
                    .to("mock:middle")
                    //throw a second time to validate that the exchange is reset appropriately
                    .throwException(new IllegalArgumentException("Forced Again"))
                    .to("mock:result");
            }
        };
    }
}