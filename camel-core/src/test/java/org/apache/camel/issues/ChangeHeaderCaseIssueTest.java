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
package org.apache.camel.issues;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class ChangeHeaderCaseIssueTest extends ContextTestSupport {

    public void testChangeHeaderCaseIssue() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("SoapAction", "cool");

        template.sendBodyAndHeader("direct:start", "Hello World", "SOAPAction", "cool");

        assertMockEndpointsSatisfied();

        // only the changed case header should exist
        Map<String, Object> headers = new HashMap<String, Object>(mock.getReceivedExchanges().get(0).getIn().getHeaders());
        assertEquals("cool", headers.get("SoapAction"));
        assertEquals(null, headers.get("SOAPAction"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("mock:result").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        // change the case of the header
                        Object value = exchange.getIn().removeHeader("SOAPAction");
                        exchange.getIn().setHeader("SoapAction", value);
                    }
                });

                from("direct:start").to("mock:result");
            }
        };
    }
}
