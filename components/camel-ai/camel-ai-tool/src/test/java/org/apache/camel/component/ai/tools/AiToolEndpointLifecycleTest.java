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
package org.apache.camel.component.ai.tools;

import java.util.Set;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AiToolEndpointLifecycleTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-tool:getWeather"
                     + "?tags=weather"
                     + "&description=Get the current weather for a city"
                     + "&parameter.city=string"
                     + "&parameter.city.description=The city name"
                     + "&parameter.city.required=true"
                     + "&parameter.unit=string"
                     + "&parameter.unit.enum=celsius,fahrenheit")
                        .process(exchange -> {
                            AiToolArguments args
                                    = exchange.getVariable(AiTool.TOOL_ARGUMENTS, AiToolArguments.class);
                            String city = args != null ? args.getString("city") : "unknown";
                            exchange.getMessage().setBody("Sunny in " + city);
                        });
            }
        };
    }

    @Test
    public void testToolRegisteredOnStart() {
        AiToolRegistry registry = AiToolRegistry.getOrCreate(context);

        Set<AiToolSpec> tools = registry.getTools().get("weather");
        assertThat(tools)
                .as("Tools registered under 'weather' tag")
                .isNotNull()
                .hasSize(1);

        AiToolSpec spec = tools.iterator().next();
        assertThat(spec.getName())
                .as("Tool name")
                .isEqualTo("getWeather");
        assertThat(spec.getDescription())
                .as("Tool description")
                .isEqualTo("Get the current weather for a city");
        assertThat(spec.getConsumer())
                .as("Tool consumer")
                .isNotNull();
        assertThat(spec.getParametersJsonSchema())
                .as("Tool JSON schema")
                .isNotNull();
        assertThat(spec.getParameterDefs())
                .as("Tool parameter definitions")
                .isNotNull()
                .hasSize(2)
                .containsKey("city")
                .containsKey("unit");
        assertThat(spec.getParameterDefs().get("city").isRequired())
                .as("City parameter should be required")
                .isTrue();
    }

    @Test
    public void testToolDeregisteredOnStop() throws Exception {
        AiToolRegistry registry = AiToolRegistry.getOrCreate(context);

        assertThat(registry.getTools().get("weather"))
                .as("Tool should be registered before stop")
                .isNotNull();

        context.stop();

        assertThat(registry.getTools().get("weather"))
                .as("Tool should be deregistered after stop")
                .isNull();
    }

    @Test
    public void testProducerThrowsUnsupported() {
        assertThatThrownBy(() -> context.getEndpoint("ai-tool:test?tags=t&description=d").createProducer())
                .as("Creating a producer should throw UnsupportedOperationException")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testToolNameUsedAsName() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-tool:getUserProfile"
                     + "?tags=test"
                     + "&description=Get user profile")
                        .setBody(constant("profile"));
            }
        });

        Set<AiToolSpec> tools = AiToolRegistry.getOrCreate(context).getTools().get("test");
        assertThat(tools)
                .as("Tools registered under 'test' tag")
                .isNotNull()
                .hasSize(1)
                .first()
                .satisfies(spec -> assertThat(spec.getName())
                        .as("Tool name should match toolName")
                        .isEqualTo("getUserProfile"));
    }

    @Test
    public void testTaglessToolRegisteredInDefaultPool() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-tool:defaultTool"
                     + "?description=A tool with no tags")
                        .setBody(constant("default"));
            }
        });

        AiToolRegistry registry = AiToolRegistry.getOrCreate(context);
        assertThat(registry.getDefaultTools())
                .as("Default pool should contain the tagless tool")
                .hasSize(1)
                .first()
                .satisfies(spec -> assertThat(spec.getName())
                        .as("Tool name")
                        .isEqualTo("defaultTool"));
    }

    @Test
    public void testTaglessToolDeregisteredOnStop() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-tool:tempTool"
                     + "?description=Temporary tool")
                        .setBody(constant("temp"));
            }
        });

        AiToolRegistry registry = AiToolRegistry.getOrCreate(context);
        assertThat(registry.getDefaultTools())
                .as("Default pool should contain the tool before stop")
                .isNotEmpty();

        context.stop();

        assertThat(registry.getDefaultTools())
                .as("Default pool should be empty after stop")
                .isEmpty();
    }

    @Test
    public void testMissingDescriptionDefaultsToToolName() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-tool:noDesc?tags=test")
                        .setBody(constant("no description"));
            }
        });

        AiToolRegistry registry = AiToolRegistry.getOrCreate(context);
        Set<AiToolSpec> tools = registry.getToolsByTag("test");
        assertThat(tools)
                .as("Tool should be registered even without explicit description")
                .hasSize(1)
                .first()
                .satisfies(spec -> assertThat(spec.getDescription())
                        .as("Description should default to toolName")
                        .isEqualTo("noDesc"));
    }

    @Test
    public void testMultipleTagsRegistration() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-tool:sharedTool"
                     + "?tags=assistant,admin"
                     + "&description=Shared tool")
                        .setBody(constant("shared"));
            }
        });

        AiToolRegistry registry = AiToolRegistry.getOrCreate(context);
        assertThat(registry.getTools().get("assistant"))
                .as("Tool should be registered under 'assistant' tag")
                .isNotNull()
                .hasSize(1);
        assertThat(registry.getTools().get("admin"))
                .as("Tool should be registered under 'admin' tag")
                .isNotNull()
                .hasSize(1);
    }

    @Test
    public void testMultipleTagsDeregistrationOnStop() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-tool:multiTagTool"
                     + "?tags=tagA,tagB"
                     + "&description=Multi-tag tool")
                        .setBody(constant("multi"));
            }
        });

        AiToolRegistry registry = AiToolRegistry.getOrCreate(context);
        assertThat(registry.getToolsByTag("tagA")).isNotEmpty();
        assertThat(registry.getToolsByTag("tagB")).isNotEmpty();

        context.stop();

        assertThat(registry.getTools().get("tagA"))
                .as("Tool should be deregistered from tagA after stop")
                .isNull();
        assertThat(registry.getTools().get("tagB"))
                .as("Tool should be deregistered from tagB after stop")
                .isNull();
    }

    @Test
    public void testGetToolsByTagMergesDefaultPool() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-tool:taggedTool"
                     + "?tags=myTag"
                     + "&description=A tagged tool")
                        .setBody(constant("tagged"));

                from("ai-tool:defaultTool2"
                     + "?description=A default pool tool")
                        .setBody(constant("default"));
            }
        });

        AiToolRegistry registry = AiToolRegistry.getOrCreate(context);
        Set<AiToolSpec> merged = registry.getToolsByTag("myTag");

        assertThat(merged)
                .as("getToolsByTag should return tagged + default pool tools")
                .hasSize(2)
                .extracting(AiToolSpec::getName)
                .containsExactlyInAnyOrder("taggedTool", "defaultTool2");
    }

    @Test
    public void testGetAllToolsReturnsAllPools() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-tool:toolA?tags=alpha&description=Tool A")
                        .setBody(constant("a"));
                from("ai-tool:toolB?tags=beta&description=Tool B")
                        .setBody(constant("b"));
                from("ai-tool:toolC?description=Tool C")
                        .setBody(constant("c"));
            }
        });

        AiToolRegistry registry = AiToolRegistry.getOrCreate(context);
        Set<AiToolSpec> all = registry.getAllTools();

        assertThat(all)
                .as("getAllTools should return tools from all tags and default pool")
                .extracting(AiToolSpec::getName)
                .contains("toolA", "toolB", "toolC");
    }
}
