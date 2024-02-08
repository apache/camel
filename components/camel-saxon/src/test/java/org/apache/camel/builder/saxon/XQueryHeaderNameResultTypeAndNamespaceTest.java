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
import org.apache.camel.support.builder.Namespaces;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Test XPath DSL with the ability to apply XPath on a header
 */
public class XQueryHeaderNameResultTypeAndNamespaceTest extends CamelTestSupport {

    @Test
    public void testXPathWithNamespace() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:55");
        mock.expectedBodiesReceived("body");
        mock.expectedHeaderReceived("cheeseDetails", "<number xmlns=\"http://acme.com/cheese\">55</number>");

        template.sendBodyAndHeader("direct:in", "body", "cheeseDetails",
                "<number xmlns=\"http://acme.com/cheese\">55</number>");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                Namespaces ns = new Namespaces("c", "http://acme.com/cheese");
                var xq = expression().xquery().expression("/c:number = 55").namespaces(ns).resultType(Integer.class)
                        .source("header:cheeseDetails").end();

                from("direct:in").choice()
                        .when(xq)
                            .to("mock:55")
                        .otherwise()
                            .to("mock:other")
                        .end();
            }
        };
    }
}
