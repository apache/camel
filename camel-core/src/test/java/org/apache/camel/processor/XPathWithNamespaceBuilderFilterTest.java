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
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.NamespaceBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import static org.apache.camel.builder.xml.NamespaceBuilder.namespaceContext;

/**
 * @version $Revision: 1.1 $
 */
public class XPathWithNamespaceBuilderFilterTest extends ContextTestSupport {
    protected Endpoint<Exchange> startEndpoint;
    protected MockEndpoint resultEndpoint;

    public void testSendMatchingMessage() throws Exception {
        resultEndpoint.expectedMessageCount(1);

        template.sendBody("direct:start",
                          "<person xmlns='http://acme.com/cheese' name='James' city='London'/>");

        resultEndpoint.assertIsSatisfied();
    }

    public void testSendNotMatchingMessage() throws Exception {
        resultEndpoint.expectedMessageCount(0);

        template.sendBody("direct:start",
                          "<person xmlns='http://acme.com/cheese'  name='Hiram' city='Tampa'/>");

        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        startEndpoint = resolveMandatoryEndpoint("direct:start");
        resultEndpoint = getMockEndpoint("mock:result");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                // lets define the namespaces we'll need in our filters
                NamespaceBuilder ns = namespaceContext("c", "http://acme.com/cheese")
                    .namespace("xsd", "http://www.w3.org/2001/XMLSchema");

                from("direct:start").filter(ns.xpath("/c:person[@name='James']")).to("mock:result");
                // END SNIPPET: example
            }
        };
    }

}
