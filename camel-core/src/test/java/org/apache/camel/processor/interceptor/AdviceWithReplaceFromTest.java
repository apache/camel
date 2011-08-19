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
package org.apache.camel.processor.interceptor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;

/**
 * Advice with match multiple ids test
 */
public class AdviceWithReplaceFromTest extends ContextTestSupport {

    public void testReplaceFromUri() throws Exception {
        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                // replace the input in the route with a new endpoint uri
                replaceFromWith("seda:foo");
            }
        });

        getMockEndpoint("mock:result").expectedMessageCount(1);

        // has been replaced to a seda endpoint instead
        template.sendBody("seda:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testReplaceFromEndpoint() throws Exception {
        final Endpoint endpoint = context.getEndpoint("seda:foo");

        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                // replace the input in the route with a new endpoint
                replaceFromWith(endpoint);
            }
        });

        getMockEndpoint("mock:result").expectedMessageCount(1);

        // has been replaced to a seda endpoint instead
        template.sendBody("seda:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testReplaceFromInvalidUri() throws Exception {
        try {
            context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
                @Override
                public void configure() throws Exception {
                    replaceFromWith("xxx:foo");
                }
            });
            fail("Should have thrown exception");
        } catch (FailedToCreateRouteException e) {
            assertIsInstanceOf(ResolveEndpointFailedException.class, e.getCause());
        }
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