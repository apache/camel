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
package org.apache.camel.component.ai.prompt;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiPromptEndpointLifecycleTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-prompt:app_prompt?tags=crm&description=Current application prompt")
                        .routeId("app-prompt-route")
                        .setBody(constant("Summarize the application"));

                from("ai-prompt:hidden_prompt?description=Untagged prompt")
                        .routeId("hidden-prompt-route")
                        .setBody(constant("Hidden"));
            }
        };
    }

    @Test
    void testPromptRegisteredOnStart() {
        assertThat(AiPromptRegistry.getOrCreate(context).getPrompts().get("crm"))
                .hasSize(1)
                .extracting(AiPromptSpec::getName)
                .containsExactly("app_prompt");
    }

    @Test
    void testUntaggedPromptGoesToDefaultPool() {
        AiPromptRegistry registry = AiPromptRegistry.getOrCreate(context);
        assertThat(registry.getDefaultPrompts()).extracting(AiPromptSpec::getName).containsExactly("hidden_prompt");
        assertThat(registry.getPrompts()).containsOnlyKeys("crm");
    }

    @Test
    void testPromptDeregisteredOnStopAndRegisteredOnStart() throws Exception {
        AiPromptRegistry registry = AiPromptRegistry.getOrCreate(context);
        context.getRouteController().stopRoute("app-prompt-route");
        assertThat(registry.getPrompts()).doesNotContainKey("crm");

        context.getRouteController().startRoute("app-prompt-route");
        assertThat(registry.getPrompts().get("crm")).extracting(AiPromptSpec::getName).containsExactly("app_prompt");
    }

    @Test
    void testPromptDeregisteredOnSuspendAndRegisteredOnResume() throws Exception {
        AiPromptRegistry registry = AiPromptRegistry.getOrCreate(context);
        context.getRouteController().suspendRoute("app-prompt-route");
        assertThat(registry.getPrompts()).doesNotContainKey("crm");

        context.getRouteController().resumeRoute("app-prompt-route");
        assertThat(registry.getPrompts().get("crm")).extracting(AiPromptSpec::getName).containsExactly("app_prompt");
    }

    @Test
    void testUnknownArgumentOptionFailsWhenEndpointIsCreated() throws Exception {
        try (DefaultCamelContext camelContext = new DefaultCamelContext()) {
            assertThatThrownBy(() -> camelContext.getEndpoint("ai-prompt:bad?argument.name.unknown=value"))
                    .hasStackTraceContaining("Unsupported ai-prompt argument option");
        }
    }

    @Test
    void testInvalidRequiredArgumentFailsWhenEndpointIsCreated() throws Exception {
        try (DefaultCamelContext camelContext = new DefaultCamelContext()) {
            assertThatThrownBy(() -> camelContext.getEndpoint("ai-prompt:bad?argument.name.required=yes"))
                    .hasStackTraceContaining("Invalid required flag for prompt argument 'name'");
        }
    }

    @Test
    void testReservedArgumentNameFailsWhenEndpointIsCreated() throws Exception {
        try (DefaultCamelContext camelContext = new DefaultCamelContext()) {
            assertThatThrownBy(() -> camelContext.getEndpoint("ai-prompt:bad?argument.camelCase.required=true"))
                    .hasStackTraceContaining("Prompt argument name 'camelCase' is reserved");
        }
    }
}
