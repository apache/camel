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

import net.thisptr.jackson.jq.v2.core.Environment;
import net.thisptr.jackson.jq.v2.json.JsonProvider;
import net.thisptr.jackson.jq.v2.spi.BindContext;
import net.thisptr.jackson.jq.v2.spi.Expression;
import net.thisptr.jackson.jq.v2.spi.Function;
import net.thisptr.jackson.jq.v2.spi.FunctionSignature;
import net.thisptr.jackson.jq.v2.spi.RuntimeContext;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

public class JqCustomEnvironmentTest extends JqTestSupport {

    @Test
    public void testCustomEnvironmentFunction() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(MAPPER.createObjectNode().put("foo", "camel bar"));

        template.sendBody("direct:containsCamel", MAPPER.createObjectNode().put("foo", "baz"));
        template.sendBody("direct:containsCamel", MAPPER.createObjectNode().put("foo", "camel bar"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testCustomEnvironmentBuiltInFunction() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(MAPPER.createObjectNode().put("foo", "123456"));

        template.sendBody("direct:builtInFunction", MAPPER.createObjectNode().put("foo", "12345"));
        template.sendBody("direct:builtInFunction", MAPPER.createObjectNode().put("foo", "123456"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @BindToRegistry
    public Environment<JsonNode> customEnvironment() {
        return JqFunctions.newEnvironmentBuilder(context)
                .defineFunction(FunctionSignature.of("containsCamel", 1), new ContainsFunction("camel"))
                .build();
    }

    /**
     * A jq function that tells whether its argument contains the given text.
     */
    public static class ContainsFunction implements Function {
        private final String text;

        public ContainsFunction(String text) {
            this.text = text;
        }

        @Override
        public <C extends RuntimeContext, J> Expression<C, J> bind(BindContext<J> bindCtx, List<Expression<C, J>> args) {
            JsonProvider<J> provider = bindCtx.getJsonProvider();
            return (ctx, in, ipath, output) -> args.get(0).apply(ctx, in, ipath,
                    (value, path) -> output.emit(provider.createBoolean(provider.getString(value).contains(text)), null));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:containsCamel")
                        .filter().jq("containsCamel(.foo)")
                        .to("mock:result");

                from("direct:builtInFunction")
                        .filter().jq(".foo | length > 5")
                        .to("mock:result");

            }
        };
    }
}
