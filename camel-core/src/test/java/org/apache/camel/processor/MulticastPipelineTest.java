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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

public class MulticastPipelineTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testPlainPipeline() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .pipeline("direct:a", "direct:b")
                    .pipeline("direct:c", "direct:d")
                    .to("mock:result");

                from("direct:a").to("mock:a").setBody().constant("A");
                from("direct:b").to("mock:b").setBody().constant("B");
                from("direct:c").to("mock:c").setBody().constant("C");
                from("direct:d").to("mock:d").setBody().constant("D");
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("A");
        getMockEndpoint("mock:c").expectedBodiesReceived("B");
        getMockEndpoint("mock:d").expectedBodiesReceived("C");
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testPlainPipelineTo() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .pipeline().to("direct:a", "direct:b").end()
                    .pipeline().to("direct:c", "direct:d").end()
                    .to("mock:result");

                from("direct:a").to("mock:a").setBody().constant("A");
                from("direct:b").to("mock:b").setBody().constant("B");
                from("direct:c").to("mock:c").setBody().constant("C");
                from("direct:d").to("mock:d").setBody().constant("D");
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("A");
        getMockEndpoint("mock:c").expectedBodiesReceived("B");
        getMockEndpoint("mock:d").expectedBodiesReceived("C");
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testMulticastPipeline() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .multicast()
                        .pipeline("direct:a", "direct:b")
                        .pipeline("direct:c", "direct:d")
                    .end()
                    .to("mock:result");

                from("direct:a").to("mock:a").setBody().constant("A");
                from("direct:b").to("mock:b").setBody().constant("B");
                from("direct:c").to("mock:c").setBody().constant("C");
                from("direct:d").to("mock:d").setBody().constant("D");
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("A");
        getMockEndpoint("mock:c").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:d").expectedBodiesReceived("C");
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testMulticastPipelineTo() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .multicast()
                        .pipeline().to("direct:a", "direct:b").end()
                        .pipeline().to("direct:c", "direct:d").end()
                    .end()
                    .to("mock:result");

                from("direct:a").to("mock:a").setBody().constant("A");
                from("direct:b").to("mock:b").setBody().constant("B");
                from("direct:c").to("mock:c").setBody().constant("C");
                from("direct:d").to("mock:d").setBody().constant("D");
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("A");
        getMockEndpoint("mock:c").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:d").expectedBodiesReceived("C");
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

}
