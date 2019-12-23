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
package org.apache.camel.processor.routingslip;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class RoutingSlipTest extends ContextTestSupport {

    protected static final String ANSWER = "answer";
    protected static final String ROUTING_SLIP_HEADER = "myHeader";

    @Test
    public void testUpdatingOfRoutingSlipAllDefaults() throws Exception {
        MockEndpoint x = getMockEndpoint("mock:x");
        MockEndpoint y = getMockEndpoint("mock:y");
        MockEndpoint z = getMockEndpoint("mock:z");

        x.expectedBodiesReceived(ANSWER);
        y.expectedBodiesReceived(ANSWER);
        z.expectedBodiesReceived(ANSWER);

        sendBody("direct:a", ROUTING_SLIP_HEADER, ",");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUpdatingOfRoutingSlipHeaderSet() throws Exception {
        MockEndpoint x = getMockEndpoint("mock:x");
        MockEndpoint y = getMockEndpoint("mock:y");
        MockEndpoint z = getMockEndpoint("mock:z");

        x.expectedBodiesReceived(ANSWER);
        y.expectedBodiesReceived(ANSWER);
        z.expectedBodiesReceived(ANSWER);

        sendBody("direct:b", "aRoutingSlipHeader", ",");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUpdatingOfRoutingSlipHeaderAndDelimiterSet() throws Exception {
        MockEndpoint x = getMockEndpoint("mock:x");
        MockEndpoint y = getMockEndpoint("mock:y");
        MockEndpoint z = getMockEndpoint("mock:z");

        x.expectedBodiesReceived(ANSWER);
        y.expectedBodiesReceived(ANSWER);
        z.expectedBodiesReceived(ANSWER);

        sendBody("direct:c", "aRoutingSlipHeader", "#");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testBodyExpression() throws Exception {
        MockEndpoint x = getMockEndpoint("mock:x");
        MockEndpoint y = getMockEndpoint("mock:y");
        MockEndpoint z = getMockEndpoint("mock:z");

        x.expectedBodiesReceived("mock:x, mock:y,mock:z");
        y.expectedBodiesReceived("mock:x, mock:y,mock:z");
        z.expectedBodiesReceived("mock:x, mock:y,mock:z");

        template.sendBody("direct:d", "mock:x, mock:y,mock:z");
        assertMockEndpointsSatisfied();

    }

    @Test
    public void testMessagePassingThrough() throws Exception {
        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedMessageCount(1);

        sendBody("direct:a", ROUTING_SLIP_HEADER, ",");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEmptyRoutingSlip() throws Exception {
        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedMessageCount(1);

        sendBodyWithEmptyRoutingSlip();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNoRoutingSlip() throws Exception {
        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedMessageCount(1);

        sendBodyWithNoRoutingSlip();

        assertMockEndpointsSatisfied();
    }

    protected void sendBody(String endpoint, String header, String delimiter) {
        template.sendBodyAndHeader(endpoint, ANSWER, header, "mock:x" + delimiter + "mock:y" + delimiter + "mock:z");
    }

    protected void sendBodyWithEmptyRoutingSlip() {
        template.sendBodyAndHeader("direct:a", ANSWER, ROUTING_SLIP_HEADER, "");
    }

    protected void sendBodyWithNoRoutingSlip() {
        template.sendBody("direct:a", ANSWER);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1
                from("direct:a").routingSlip(header("myHeader")).to("mock:end");
                // END SNIPPET: e1

                // START SNIPPET: e2
                from("direct:b").routingSlip(ExpressionBuilder.headerExpression("aRoutingSlipHeader"));
                // END SNIPPET: e2

                // START SNIPPET: e3
                from("direct:c").routingSlip(header("aRoutingSlipHeader"), "#");
                // END SNIPPET: e3

                // START SNIPPET: e4
                from("direct:d").routingSlip(body());
                // END SNIPPET: e4
            }
        };
    }
}
