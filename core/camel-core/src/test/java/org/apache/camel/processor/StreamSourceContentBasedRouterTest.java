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
package org.apache.camel.processor;

import java.io.StringReader;

import javax.xml.transform.stream.StreamSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.xml.StringSource;
import org.junit.Before;
import org.junit.Test;

/**
 * Test for handling a StreamSource in a content-based router with XPath
 * predicates
 */
public class StreamSourceContentBasedRouterTest extends ContextTestSupport {
    protected MockEndpoint x;
    protected MockEndpoint y;

    @Test
    public void testSendStreamSource() throws Exception {
        x.expectedMessageCount(1);
        y.expectedMessageCount(1);

        sendBody("direct:start", new StreamSource(new StringReader("<message>xx</message>")));
        sendBody("direct:start", new StreamSource(new StringReader("<message>yy</message>")));

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendStringSource() throws Exception {
        x.expectedMessageCount(1);
        y.expectedMessageCount(1);

        sendBody("direct:start", new StringSource("<message>xx</message>"));
        sendBody("direct:start", new StringSource("<message>yy</message>"));

        assertMockEndpointsSatisfied();
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        x = getMockEndpoint("mock:x");
        y = getMockEndpoint("mock:y");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // should work with default error handler as the stream cache
                // is enabled and make sure the predicates can be evaluated
                // multiple times

                from("direct:start").streamCaching().choice().when().xpath("/message/text() = 'xx'").to("mock:x").when().xpath("/message/text() = 'yy'").to("mock:y");
            }
        };
    }

}
