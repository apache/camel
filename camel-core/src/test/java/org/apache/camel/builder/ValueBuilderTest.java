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
package org.apache.camel.builder;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class ValueBuilderTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testAppend() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").transform(body().append(" World")).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.message(0).body().isEqualToIgnoreCase("hello WORLD");

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();
    }

    public void testPrepend() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").transform(body().prepend("Hello ")).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.message(0).body().isEqualToIgnoreCase("hello world");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    public void testMatches() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.message(0).body().matches(new Expression() {
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                String body = exchange.getIn().getBody(String.class);
                Boolean answer = body.contains("Camel");
                return type.cast(answer);
            }
        });

        template.sendBody("direct:start", "Camel rocks");
        mock.assertIsSatisfied();

        // send in a false test
        mock.reset();
        mock.message(0).body().matches(new Expression() {
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                String body = exchange.getIn().getBody(String.class);
                Boolean answer = body.contains("Camel");
                return type.cast(answer);
            }
        });
        template.sendBody("direct:start", "Hello World");
        mock.assertIsNotSatisfied();
    }
}
