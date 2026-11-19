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
package org.apache.camel.language.jq;

import java.util.List;

import net.thisptr.jackson.jq.v2.json.JsonProvider;
import net.thisptr.jackson.jq.v2.spi.BindContext;
import net.thisptr.jackson.jq.v2.spi.Expression;
import net.thisptr.jackson.jq.v2.spi.Function;
import net.thisptr.jackson.jq.v2.spi.RuntimeContext;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JqRegistryFunctionTest extends JqTestSupport {

    @Test
    public void testFunctionFromRegistry() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(MAPPER.createObjectNode().put("foo", "beer"));

        template.sendBody("direct:containsBeer", MAPPER.createObjectNode().put("foo", "baz"));
        template.sendBody("direct:containsBeer", MAPPER.createObjectNode().put("foo", "beer"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testFunctionWithArityFromRegistry() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(MAPPER.createObjectNode().put("foo", "wine"));

        template.sendBody("direct:containsWine", MAPPER.createObjectNode().put("foo", "baz"));
        template.sendBody("direct:containsWine", MAPPER.createObjectNode().put("foo", "wine"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testCamelFunctionWinsOverRegistry() throws Exception {
        getMockEndpoint("mock:header").expectedBodiesReceived("camel");

        template.sendBodyAndHeader("direct:header", MAPPER.createObjectNode(), "foo", "camel");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvalidArityFromRegistry() throws Exception {
        try (CamelContext camelContext = new DefaultCamelContext()) {
            camelContext.getRegistry().bind("containsWine/abc", containsWine());
            JqLanguage language = new JqLanguage();
            language.setCamelContext(camelContext);

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, language::init);
            assertEquals("Invalid jq function name in the registry, expected name/arity: containsWine/abc", e.getMessage());
        }
    }

    @BindToRegistry
    public Function containsBeer() {
        return new JqCustomEnvironmentTest.ContainsFunction("beer");
    }

    @BindToRegistry("containsWine/1")
    public Function containsWine() {
        return new JqCustomEnvironmentTest.ContainsFunction("wine");
    }

    @BindToRegistry("header/1")
    public Function header() {
        return new Function() {
            @Override
            public <C extends RuntimeContext, J> Expression<C, J> bind(BindContext<J> bindCtx, List<Expression<C, J>> args) {
                JsonProvider<J> provider = bindCtx.getJsonProvider();
                return (ctx, in, ipath, output) -> output.emit(provider.createString("registry"), null);
            }
        };
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:containsBeer")
                        .filter().jq("containsBeer(.foo)")
                        .to("mock:result");

                from("direct:header")
                        .transform().jq("header(\"foo\")", String.class)
                        .to("mock:header");

                from("direct:containsWine")
                        .filter().jq("containsWine(.foo)")
                        .to("mock:result");
            }
        };
    }
}
