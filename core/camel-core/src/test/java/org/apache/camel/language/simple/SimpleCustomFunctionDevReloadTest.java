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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.SimpleFunction;
import org.junit.jupiter.api.Test;

/**
 * In the dev profile (camel run --dev) a custom simple function backed by a {@link SimpleFunction} bean in the registry
 * is resolved again per evaluation, so an edited bean takes effect on live reload even though the simple language
 * caches the parsed expression.
 */
public class SimpleCustomFunctionDevReloadTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext answer = super.createCamelContext();
        answer.getCamelContextExtension().setProfile("dev");
        return answer;
    }

    @Test
    public void testFunctionBeanReplaced() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "Bye World");

        template.sendBody("direct:start", "World");

        // simulate a live reload that re-creates the bean with new behaviour
        context.getRegistry().unbind("greet");
        context.getRegistry().bind("greet", new GreetFunction("Bye"));

        template.sendBody("direct:start", "World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFunctionBeanReplacedInChain() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Earth", "Bye Earth");

        template.sendBody("direct:chain", "Earth");

        context.getRegistry().unbind("greet");
        context.getRegistry().bind("greet", new GreetFunction("Bye"));

        template.sendBody("direct:chain", "Earth");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getRegistry().bind("greet", new GreetFunction("Hello"));

                from("direct:start")
                        .setBody(simple("${greet(${body})}"))
                        .to("mock:result");

                from("direct:chain")
                        .setBody(simple("${body} ~> ${greet}"))
                        .to("mock:result");
            }
        };
    }

    private static class GreetFunction implements SimpleFunction {

        private final String greeting;

        private GreetFunction(String greeting) {
            this.greeting = greeting;
        }

        @Override
        public String getName() {
            return "greet";
        }

        @Override
        public Object apply(Exchange exchange, Object input) {
            return greeting + " " + input;
        }
    }
}
