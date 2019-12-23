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
package org.apache.camel.processor.interceptor;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.InterceptStrategy;
import org.junit.Test;

public class InterceptorStrategyNotOrderedTest extends ContextTestSupport {

    @Test
    public void testInterceptorStrategyNotOrdered() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedHeaderReceived("order", "foobar");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // interceptors should be invoked in the default order they are
                // added
                context.adapt(ExtendedCamelContext.class).addInterceptStrategy(new FooInterceptStrategy());
                context.adapt(ExtendedCamelContext.class).addInterceptStrategy(new BarInterceptStrategy());

                from("direct:start").to("mock:result");
            }
        };
    }

    private static class FooInterceptStrategy implements InterceptStrategy {

        @Override
        public Processor wrapProcessorInInterceptors(CamelContext context, NamedNode definition, final Processor target, Processor nextTarget) throws Exception {
            Processor answer = new Processor() {
                public void process(Exchange exchange) throws Exception {
                    String order = exchange.getIn().getHeader("order", "", String.class);
                    order = order + "foo";
                    exchange.getIn().setHeader("order", order);

                    target.process(exchange);
                }
            };
            return answer;
        }

    }

    private static class BarInterceptStrategy implements InterceptStrategy {

        @Override
        public Processor wrapProcessorInInterceptors(CamelContext context, NamedNode definition, final Processor target, Processor nextTarget) throws Exception {
            Processor answer = new Processor() {
                public void process(Exchange exchange) throws Exception {
                    String order = exchange.getIn().getHeader("order", "", String.class);
                    order = order + "bar";
                    exchange.getIn().setHeader("order", order);

                    target.process(exchange);
                }
            };
            return answer;
        }

    }
}
