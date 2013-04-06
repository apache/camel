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

public class SplitTokenizerNamespaceTest extends ContextTestSupport {

    public void testSplitTokenizerWithImplicitNamespaces() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        
        // we expect to receive results that have namespace definitions on each token
        // we could receive nodes from multiple namespaces since we did not specify a namespace prefix, 
        mock.expectedBodiesReceived(
            "<ns1:person xmlns:ns1=\"urn:org.apache.camel\" xmlns:ns2=\"urn:org.apache.cameltoo\">Claus</ns1:person>", 
            "<ns1:person xmlns:ns1=\"urn:org.apache.camel\" xmlns:ns2=\"urn:org.apache.cameltoo\">James</ns1:person>", 
            "<ns1:person xmlns:ns1=\"urn:org.apache.camel\" xmlns:ns2=\"urn:org.apache.cameltoo\">Willem</ns1:person>",
            "<ns2:person xmlns:ns1=\"urn:org.apache.camel\" xmlns:ns2=\"urn:org.apache.cameltoo\">Rich</ns2:person>");

        template.sendBody("direct:noPrefix", getXmlBody());

        assertMockEndpointsSatisfied();
    }

    public void testSplitTokenizerWithExplicitNamespaces() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        
        // we expect to receive results that have namespace definitions on each token
        // we provided an explicit namespace prefix value in the route, so we will only receive nodes that have a matching prefix value
        mock.expectedBodiesReceived(
            "<ns1:person xmlns:ns1=\"urn:org.apache.camel\" xmlns:ns2=\"urn:org.apache.cameltoo\">Claus</ns1:person>", 
            "<ns1:person xmlns:ns1=\"urn:org.apache.camel\" xmlns:ns2=\"urn:org.apache.cameltoo\">James</ns1:person>", 
            "<ns1:person xmlns:ns1=\"urn:org.apache.camel\" xmlns:ns2=\"urn:org.apache.cameltoo\">Willem</ns1:person>");

        template.sendBody("direct:explicitPrefix", getXmlBody());

        assertMockEndpointsSatisfied();
    }

    protected String getXmlBody() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?>\n");
        sb.append("<ns1:persons xmlns:ns1=\"urn:org.apache.camel\" xmlns:ns2=\"urn:org.apache.cameltoo\">\n");
        sb.append("  <ns1:person>Claus</ns1:person>\n");
        sb.append("  <ns1:person>James</ns1:person>\n");
        sb.append("  <ns1:person>Willem</ns1:person>\n");
        sb.append("  <ns2:person>Rich</ns2:person>\n");
        sb.append("</ns1:persons>");
        return sb.toString();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                
                from("direct:noPrefix")
                    .split().tokenizeXML("person", "persons")
                    .to("mock:split");
                
                from("direct:explicitPrefix")
                    .split().tokenizeXML("ns1:person", "ns1:persons")
                    .to("mock:split");
            }
        };
    }

}