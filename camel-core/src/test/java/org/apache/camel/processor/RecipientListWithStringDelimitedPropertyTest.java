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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class RecipientListWithStringDelimitedPropertyTest extends ContextTestSupport {

    private static final String BODY = "answer";
    private static final String PROPERTY_VALUE = "mock:x, mock:y, mock:z";

    @Test
    public void testSendingAMessageUsingMulticastReceivesItsOwnExchange() throws Exception {
        MockEndpoint x = getMockEndpoint("mock:x");
        MockEndpoint y = getMockEndpoint("mock:y");
        MockEndpoint z = getMockEndpoint("mock:z");

        x.expectedBodiesReceived(BODY);
        y.expectedBodiesReceived(BODY);
        z.expectedBodiesReceived(BODY);
        
        x.message(0).exchangeProperty("myProperty").isEqualTo(PROPERTY_VALUE);
        y.message(0).exchangeProperty("myProperty").isEqualTo(PROPERTY_VALUE);
        z.message(0).exchangeProperty("myProperty").isEqualTo(PROPERTY_VALUE);
        
        sendBody();

        assertMockEndpointsSatisfied();
    }

    protected void sendBody() {
        template.sendBodyAndProperty("direct:a", BODY, "myProperty", PROPERTY_VALUE);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                from("direct:a").recipientList(property("myProperty"));
                // END SNIPPET: example
            }
        };

    }

}