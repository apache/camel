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
package org.apache.camel.component.xquery;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test to verify that headers can be propogated through this component.
 */
public class XQueryPropogateHeadersTest extends ContextTestSupport {

    public void testPropogateHeadersTest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("<transformed subject=\"Hey\"><mail><subject>Hey</subject>"
            + "<body>Hello world!</body></mail></transformed>");
        mock.expectedHeaderReceived("foo", "bar");

        template.sendBodyAndHeader("direct:one",
            "<mail><subject>Hey</subject><body>Hello world!</body></mail>", "foo", "bar");

        assertMockEndpointsSatisfied();
    }

    public void testPropogateHeadersUsingTransform() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("London");
        mock.expectedHeaderReceived("foo", "bar");

        template.sendBodyAndHeader("direct:two",
            "<person name='James' city='London'/>", "foo", "bar");

        assertMockEndpointsSatisfied();
    }

    public void testPropogateHeadersUsingSetBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("London");
        mock.expectedHeaderReceived("foo", "bar");

        template.sendBodyAndHeader("direct:three",
            "<person name='James' city='London'/>", "foo", "bar");

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:one")
                    .to("xquery:org/apache/camel/component/xquery/transform.xquery")
                    .to("mock:result");

                from("direct:two")
                    .transform().xquery("/person/@city", String.class)
                    .to("mock:result");

                from("direct:three")
                    .setBody().xquery("/person/@city", String.class)
                    .to("mock:result");
            }
        };
    }
}
