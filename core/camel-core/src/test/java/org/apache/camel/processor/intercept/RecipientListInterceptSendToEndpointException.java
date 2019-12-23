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
package org.apache.camel.processor.intercept;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockComponent;

/**
 *
 */
public class RecipientListInterceptSendToEndpointException extends ContextTestSupport {

    public void testRoute() throws Exception {
        getMockEndpoint("mock:end").expectedMessageCount(2);
        getMockEndpoint("mock:intercept").expectedMessageCount(3);
        getMockEndpoint("ftp:foo").expectedBodiesReceived("Hello World", "Bye World");
        getMockEndpoint("http:bar").expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "ftp:foo,http:bar");
        template.sendBodyAndHeader("direct:start", "Bye World", "foo", "ftp:foo");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // simulate ftp/http using mocks
                context.addComponent("ftp", new MockComponent());
                context.addComponent("http", new MockComponent());

                interceptSendToEndpoint("(ftp|http):.*").to("log:intercept").to("mock:intercept");

                from("direct:start").recipientList(header("foo")).parallelProcessing().to("mock:end");
            }
        };
    }

}
