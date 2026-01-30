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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.SimpleFunctionRegistry;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SimpleCustomFunctionTest extends ContextTestSupport {

    @Test
    public void testCustomFunctionWithBody() throws Exception {
        SimpleFunctionRegistry reg = PluginHelper.getSimpleFunctionRegistry(context);
        Assertions.assertEquals(1, reg.size());

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello I was here World");
        template.sendBody("direct:start", "World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCustomFunctionWithExp() throws Exception {
        SimpleFunctionRegistry reg = PluginHelper.getSimpleFunctionRegistry(context);
        Assertions.assertEquals(1, reg.size());

        getMockEndpoint("mock:result").expectedBodiesReceived("Bye I was here Moon");
        template.sendBody("direct:start2", "World");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                SimpleFunctionRegistry reg = PluginHelper.getSimpleFunctionRegistry(getCamelContext());
                reg.addFunction("foo", new ExpressionAdapter() {
                    @Override
                    public Object evaluate(Exchange exchange) {
                        return "I was here " + exchange.getMessage().getBody();
                    }
                });

                from("direct:start")
                        .setBody(simple("Hello ${function(foo)}"))
                        .to("mock:result");

                from("direct:start2")
                        .setVariable("msg", constant("Moon"))
                        .setBody(simple("Bye ${function(foo,${variable.msg})}"))
                        .to("mock:result");
            }
        };
    }
}
