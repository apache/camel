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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.builder.Namespaces;
import org.junit.Test;

/**
 * XPath with namespace test
 */
public class XPathNamespaceTest extends ContextTestSupport {

    @Test
    public void testXPathWithNamespace() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:55");
        mock.expectedBodiesReceived("<number xmlns=\"http://acme.com/cheese\">55</number>");

        template.sendBody("direct:in", "<number xmlns=\"http://acme.com/cheese\">55</number>");

        mock.assertIsSatisfied();
    }

    @Test
    public void testXPathWithNamespaceOther() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:other");
        mock.expectedBodiesReceived("<number xmlns=\"http://acme.com/cheese\">99</number>");

        template.sendBody("direct:in", "<number xmlns=\"http://acme.com/cheese\">99</number>");

        mock.assertIsSatisfied();
    }

    @Test
    public void testXPathWithNamespaceDifferentNamespace() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:other");
        mock.expectedBodiesReceived("<number xmlns=\"http://acme.com/cake\">55</number>");

        template.sendBody("direct:in", "<number xmlns=\"http://acme.com/cake\">55</number>");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                Namespaces ns = new Namespaces("c", "http://acme.com/cheese");

                from("direct:in").choice().when(xpath("/c:number = 55", Integer.class, ns)).to("mock:55").otherwise().to("mock:other").end();
            }
        };
    }
}
