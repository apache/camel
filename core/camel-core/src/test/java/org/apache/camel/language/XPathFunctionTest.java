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
package org.apache.camel.language;

import org.w3c.dom.NodeList;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.mock.MockEndpoint.expectsMessageCount;

public class XPathFunctionTest extends ContextTestSupport {

    protected MockEndpoint x;
    protected MockEndpoint y;
    protected MockEndpoint z;
    protected MockEndpoint end;

    @Test
    public void testCheckHeader() throws Exception {
        String body = "<one/>";
        x.expectedBodiesReceived(body);
        // The SpringChoiceTest.java can't setup the header by Spring configure
        // file
        // x.expectedHeaderReceived("name", "a");
        expectsMessageCount(0, y, z);

        sendMessage("bar", body);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCheckBody() throws Exception {
        String body = "<two/>";
        y.expectedBodiesReceived(body);
        expectsMessageCount(0, x, z);

        sendMessage("cheese", body);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSetXpathProperty() throws Exception {
        String body = "<soapenv:Body xmlns:ns=\"http://myNamesapce\" xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">" + "<ns:Addresses> <Address>address1</Address>"
                      + " <Address>address2</Address> <Address>address3</Address>" + " <Address>address4</Address> </ns:Addresses> </soapenv:Body>";
        end.reset();
        end.expectedMessageCount(1);
        template.sendBody("direct:setProperty", body);
        assertMockEndpointsSatisfied();
        Exchange exchange = end.getExchanges().get(0);
        NodeList nodeList = exchange.getProperty("Addresses", NodeList.class);
        assertNotNull("The node list should not be null", nodeList);

    }

    protected void sendMessage(final Object headerValue, final Object body) throws Exception {
        template.sendBodyAndHeader("direct:start", body, "foo", headerValue);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        x = getMockEndpoint("mock:x");
        y = getMockEndpoint("mock:y");
        z = getMockEndpoint("mock:z");
        end = getMockEndpoint("mock:end");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                // START SNIPPET: ex
                from("direct:start").choice().when().xpath("in:header('foo') = 'bar'").to("mock:x").when().xpath("in:body() = '<two/>'").to("mock:y").otherwise().to("mock:z");
                // END SNIPPET: ex

                from("direct:setProperty").setProperty("Addresses").xpath("//Address", NodeList.class).to("mock:end");
            }
        };
    }

}
