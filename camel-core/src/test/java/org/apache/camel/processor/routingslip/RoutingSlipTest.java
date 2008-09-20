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
package org.apache.camel.processor.routingslip;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class RoutingSlipTest extends ContextTestSupport {

    protected static final String ANSWER = "answer";
    protected static final String ROUTING_SLIP_HEADER = "routingSlipHeader";

    public void testUpdatingOfRoutingSlipAllDefaults()
        throws Exception {
        MockEndpoint x = getMockEndpoint("mock:x");
        MockEndpoint y = getMockEndpoint("mock:y");
        MockEndpoint z = getMockEndpoint("mock:z");

        // at each destination, the routing slip should contain
        // the remaining destinations
        x.expectedHeaderReceived(ROUTING_SLIP_HEADER, "mock:y,mock:z");
        y.expectedHeaderReceived(ROUTING_SLIP_HEADER, "mock:z");
        z.expectedHeaderReceived(ROUTING_SLIP_HEADER, "");

        sendBody("direct:a", ROUTING_SLIP_HEADER, ",");

        assertMockEndpointsSatisfied();
    }

    public void testUpdatingOfRoutingSlipHeaderSet() throws Exception {
        MockEndpoint x = getMockEndpoint("mock:x");
        MockEndpoint y = getMockEndpoint("mock:y");
        MockEndpoint z = getMockEndpoint("mock:z");

        // at each destination, the routing slip should contain
        // the remaining destinations
        x.expectedHeaderReceived("aRoutingSlipHeader", "mock:y,mock:z");
        y.expectedHeaderReceived("aRoutingSlipHeader", "mock:z");
        z.expectedHeaderReceived("aRoutingSlipHeader", "");

        sendBody("direct:b", "aRoutingSlipHeader", ",");

        assertMockEndpointsSatisfied();
    }

    public void testUpdatingOfRoutingSlipHeaderAndDelimiterSet() throws Exception {
        MockEndpoint x = getMockEndpoint("mock:x");
        MockEndpoint y = getMockEndpoint("mock:y");
        MockEndpoint z = getMockEndpoint("mock:z");

        // at each destination, the routing slip should contain
        // the remaining destinations
        x.expectedHeaderReceived("aRoutingSlipHeader", "mock:y#mock:z");
        y.expectedHeaderReceived("aRoutingSlipHeader", "mock:z");
        z.expectedHeaderReceived("aRoutingSlipHeader", "");

        sendBody("direct:c", "aRoutingSlipHeader", "#");

        assertMockEndpointsSatisfied();
    }

    public void testMessagePassingThrough() throws Exception {
        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedMessageCount(1);

        sendBody("direct:a", ROUTING_SLIP_HEADER, ",");

        assertMockEndpointsSatisfied();
    }

    public void testEmptyRoutingSlip() throws Exception {
        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedMessageCount(1);

        sendBodyWithEmptyRoutingSlip();

        assertMockEndpointsSatisfied();
    }

    public void testNoRoutingSlip() throws Exception {
        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedMessageCount(1);

        sendBodyWithNoRoutingSlip();

        assertMockEndpointsSatisfied();
    }

    protected void sendBody(String endpoint, String header, String delimiter) {
        template.sendBodyAndHeader(endpoint, ANSWER, header,
               "mock:x" + delimiter + "mock:y" + delimiter + "mock:z");
    }

    protected void sendBodyWithEmptyRoutingSlip() {
        template.sendBodyAndHeader("direct:a", ANSWER, ROUTING_SLIP_HEADER, "");
    }

    protected void sendBodyWithNoRoutingSlip() {
        template.sendBody("direct:a", ANSWER);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1
                from("direct:a").routingSlip().to("mock:end");
                // END SNIPPET: e1

                // START SNIPPET: e2
                from("direct:b").routingSlip("aRoutingSlipHeader");
                // END SNIPPET: e2

                // START SNIPPET: e3
                from("direct:c").routingSlip("aRoutingSlipHeader", "#");
                // END SNIPPET: e3
            }
        };
    }
}
