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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import net.thisptr.jackson.jq.Expression;
import net.thisptr.jackson.jq.Function;
import net.thisptr.jackson.jq.PathOutput;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Version;
import net.thisptr.jackson.jq.exception.JsonQueryException;
import net.thisptr.jackson.jq.path.Path;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class JqCustomScopeTest extends JqTestSupport {

    @Test
    public void testCustomScopeFunction() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(MAPPER.createObjectNode().put("foo", "camel bar"));

        template.sendBody("direct:containsCamel", MAPPER.createObjectNode().put("foo", "baz"));
        template.sendBody("direct:containsCamel", MAPPER.createObjectNode().put("foo", "camel bar"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testCustomScopeFunctionFromRegistry() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(MAPPER.createObjectNode().put("foo", "beer"));

        template.sendBody("direct:containsBeer", MAPPER.createObjectNode().put("foo", "baz"));
        template.sendBody("direct:containsBeer", MAPPER.createObjectNode().put("foo", "beer"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testCustomScopeBuiltInFunction() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(MAPPER.createObjectNode().put("foo", "123456"));

        template.sendBody("direct:builtInFunction", MAPPER.createObjectNode().put("foo", "12345"));
        template.sendBody("direct:builtInFunction", MAPPER.createObjectNode().put("foo", "123456"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @BindToRegistry
    public Scope customScope() {
        Scope scope = Scope.newEmptyScope();
        JqFunctions.load(context, scope);
        scope.addFunction("containsCamel", new Function() {
            @Override
            public void apply(Scope scope, List<Expression> args, JsonNode in, Path path, PathOutput output, Version version)
                    throws JsonQueryException {
                args.get(0).apply(scope, in, (value) -> {
                    output.emit(BooleanNode.valueOf(value.asText().contains("camel")), null);
                });
            }
        });
        return scope;
    }

    @BindToRegistry
    public Function containsBeer() {
        return new Function() {
            @Override
            public void apply(Scope scope, List<Expression> args, JsonNode in, Path path, PathOutput output, Version version)
                    throws JsonQueryException {
                args.get(0).apply(scope, in, (value) -> {
                    output.emit(BooleanNode.valueOf(value.asText().contains("beer")), null);
                });
            }
        };
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:containsCamel")
                        .filter().jq("containsCamel(.foo)")
                        .to("mock:result");

                from("direct:containsBeer")
                        .filter().jq("containsBeer(.foo)")
                        .to("mock:result");

                from("direct:builtInFunction")
                        .filter().jq(".foo | length > 5")
                        .to("mock:result");

            }
        };
    }
}
