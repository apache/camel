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
package org.apache.camel.builder.xml;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class XPathTransformRouteTest extends ContextTestSupport {

    public Document replaceMe(Document doc) throws Exception {
        // replace firstname to contain Servicemix
        NodeList list = doc.getElementsByTagName("firstname");
        list.item(0).setTextContent("Servicemix");
        // return the changed document
        return doc;
    }

    @Test
    public void testXPathTransform() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("<root><firstname>Servicemix</firstname><lastname>Camel</lastname></root>");

        template.sendBody("direct:start", "<root><firstname>Apache</firstname><lastname>Camel</lastname></root>");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").bean(XPathTransformRouteTest.class, "replaceMe").to("log:result", "mock:result");
            }
        };
    }
}
