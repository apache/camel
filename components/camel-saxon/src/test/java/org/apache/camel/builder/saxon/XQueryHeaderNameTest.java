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
package org.apache.camel.builder.saxon;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Test XQuery DSL with the ability to apply XPath on a header
 */
public class XQueryHeaderNameTest extends CamelTestSupport {
    @Test
    public void testChoiceWithHeaderNamePremium() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:premium");
        mock.expectedBodiesReceived("<response>OK</response>");
        mock.expectedHeaderReceived("invoiceDetails", "<invoice orderType='premium'><person><name>Alan</name></person></invoice>");
 
        template.sendBodyAndHeader("direct:in", "<response>OK</response>",
                                   "invoiceDetails", "<invoice orderType='premium'><person><name>Alan</name></person></invoice>");

        mock.assertIsSatisfied();
    }

    @Test
    public void testChoiceWithHeaderNameStandard() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:standard");
        mock.expectedBodiesReceived("<response>OK</response>");
        mock.expectedHeaderReceived("invoiceDetails", "<invoice orderType='standard'><person><name>Alan</name></person></invoice>");
 
        template.sendBodyAndHeader("direct:in", "<response>OK</response>",
                                   "invoiceDetails", "<invoice orderType='standard'><person><name>Alan</name></person></invoice>");

        mock.assertIsSatisfied();
    }
    
    @Test
    public void testChoiceWithHeaderNameUnknown() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unknown");
        mock.expectedBodiesReceived("<response>OK</response>");
        mock.expectedHeaderReceived("invoiceDetails", "<invoice />");
 
        template.sendBodyAndHeader("direct:in", "<response>OK</response>",
                                   "invoiceDetails", "<invoice />");

        mock.assertIsSatisfied();
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:in")
                    .choice()
                        .when().xquery("/invoice/@orderType = 'premium'", "invoiceDetails")
                            .to("mock:premium")
                        .when().xquery("/invoice/@orderType = 'standard'", "invoiceDetails")
                            .to("mock:standard")
                        .otherwise()
                            .to("mock:unknown")
                        .end();
            }
        };
    }
}
