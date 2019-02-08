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
package org.apache.camel.builder.xml;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class XPathContentBasedRouterTest extends ContextTestSupport {

    @Test
    public void testXPathContentBasedRouter() throws Exception {
        getMockEndpoint("mock:english").expectedBodiesReceived("<hello/>");
        getMockEndpoint("mock:dutch").expectedBodiesReceived("<hallo/>");
        getMockEndpoint("mock:german").expectedBodiesReceived("<hallo/>");
        getMockEndpoint("mock:french").expectedBodiesReceived("<hellos/>");

        template.sendBody("direct:a", "<hello/>");
        template.sendBody("direct:a", "<hallo/>");
        template.sendBody("direct:a", "<hellos/>");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a")
                    .choice()
                        .when(xpath("//hello")).to("mock:english")
                        .when(xpath("//hallo")).to("mock:dutch", "mock:german")
                        .otherwise().to("mock:french");
            }
        };
    }
}
