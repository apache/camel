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
package org.apache.camel.language.simple;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class SimplePrettyTest extends ContextTestSupport {

    private static final String XML = """
            <person>
              <name>
                Jack
              </name>
            </person>""";

    private static final String JSON = """
            {
            	"name": "Jack",
            	"age": 44
            }
            """;

    @Test
    public void testPrettyXml() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(XML);

        template.sendBody("direct:xml", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPrettyJSon() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(JSON);

        template.sendBody("direct:json", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPrettyText() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:text", "World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:xml")
                        .setBody().simple("<person><name>Jack</name></person>", true)
                        .to("mock:result");

                from("direct:json")
                        .setBody().simple("{ \"name\": \"Jack\", \"age\": 44 }", true)
                        .to("mock:result");

                from("direct:text")
                        .setBody().simple("Hello ${body}", true)
                        .to("mock:result");
            }
        };
    }
}
