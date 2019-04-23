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
import org.junit.Test;

import static org.apache.camel.builder.PredicateBuilder.not;
import static org.apache.camel.language.xpath.XPathBuilder.xpath;

public class XPathMockTest extends ContextTestSupport {

    @Test
    public void testXPathMock() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.message(0).body().matches(xpath("/foo/text() = 'Hello World'").booleanResult());

        template.sendBody("direct:start", "<foo>Hello World</foo>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testXPathMock2() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.message(0).predicate().xpath("/foo/text() = 'Hello World'");

        template.sendBody("direct:start", "<foo>Hello World</foo>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testXPathMock2Fail() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.message(0).predicate().xpath("/foo/text() = 'Bye World'");

        template.sendBody("direct:start", "<foo>Hello World</foo>");

        mock.assertIsNotSatisfied();
    }

    @Test
    public void testXPathMock3() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.message(0).predicate().xpath("/foo/text() = 'Hello World'");

        template.sendBody("direct:start", "<foo>Hello World</foo>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testXPathMockMatches() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessagesMatches(xpath("/foo/text() = 'Hello World'"));

        template.sendBody("direct:start", "<foo>Hello World</foo>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testXPathMockMatchesTwo() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessagesMatches(xpath("/foo/text() = 'Hello World'"), xpath("/foo/text() = 'Bye World'"));

        template.sendBody("direct:start", "<foo>Hello World</foo>");
        template.sendBody("direct:start", "<foo>Bye World</foo>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNonXPathMockMatches() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessagesMatches(not(body().contains("Bye")), body().contains("World"));

        template.sendBody("direct:start", "<foo>Hello World</foo>");
        template.sendBody("direct:start", "<foo>Bye World</foo>");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:result");
            }
        };
    }
}
