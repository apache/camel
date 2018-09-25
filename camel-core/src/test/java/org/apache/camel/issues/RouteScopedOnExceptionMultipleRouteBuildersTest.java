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
package org.apache.camel.issues;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;


public class RouteScopedOnExceptionMultipleRouteBuildersTest extends ContextTestSupport {

    @Test
    public void testOnExceptionErrorHandlerRef() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:handled").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(0);
        getMockEndpoint("mock:exc").expectedMessageCount(1);

        template.sendBody("direct:foo", "Hello Foo");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOnExceptionErrorHandlerRefFoo() throws Exception {
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:handled").expectedMessageCount(1);
        getMockEndpoint("mock:dead").expectedMessageCount(0);
        getMockEndpoint("mock:exc").expectedMessageCount(0);

        template.sendBody("direct:bar", "Hello Bar");

        assertMockEndpointsSatisfied();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = new DefaultCamelContext(createRegistry());
        context.setErrorHandlerBuilder(new DeadLetterChannelBuilder("mock:dead"));
        context.setLazyLoadTypeConverters(isLazyLoadingTypeConverter());
        return context;
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        return new RouteBuilder[]{
            new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from("direct:bar")
                            .onException(IllegalArgumentException.class)
                            .handled(true)
                            .to("mock:handled")
                            .end()
                                .to("mock:bar")
                                .throwException(new IllegalArgumentException("Damn"));

                    }
                },
            new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        
                        from("direct:foo")
                            .onException(Exception.class)
                            .handled(true)
                            .to("mock:exc")
                            .end()
                                .to("mock:foo")
                                .throwException(new IllegalArgumentException("Damn"));

                    }
                }
        };
    }
}