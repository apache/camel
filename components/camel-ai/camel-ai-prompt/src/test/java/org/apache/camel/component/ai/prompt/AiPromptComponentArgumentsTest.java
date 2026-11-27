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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The argument.* options of an endpoint are added to the arguments of the component, not used instead of them.
 */
class AiPromptComponentArgumentsTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        Map<String, String> arguments = new LinkedHashMap<>();
        arguments.put("locale.description", "The locale");
        camelContext.getComponent("ai-prompt", AiPromptComponent.class).getConfiguration().setArguments(arguments);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-prompt:componentOnly?tags=arguments")
                        .setBody(constant("component only"));

                from("ai-prompt:withOwnArgument?tags=arguments&argument.accountId.required=true")
                        .setBody(simple("${header.locale} ${header.accountId}"));

                from("ai-prompt:requiresLocale?tags=arguments&argument.locale.required=true")
                        .setBody(constant("requires locale"));
            }
        };
    }

    @Test
    void promptWithoutArgumentOptionsDeclaresTheComponentArguments() {
        assertThat(findSpec("componentOnly").getArguments()).containsExactly(
                new AiPromptArgument("locale", "The locale", false));
    }

    @Test
    void argumentOptionsOfTheEndpointAreAddedToTheComponentArguments() {
        AiPromptSpec spec = findSpec("withOwnArgument");

        assertThat(spec.getArguments()).containsExactly(
                new AiPromptArgument("locale", "The locale", false),
                new AiPromptArgument("accountId", null, true));

        AiPromptResult result = AiPromptExecutor.execute(spec, Map.of("locale", "de", "accountId", "42"),
                spec.getConsumer().getEndpoint().createExchange());
        assertThat(result).isInstanceOf(AiPromptResult.Success.class);
        assertThat(((AiPromptResult.Success) result).value()).isEqualTo("de 42");
    }

    @Test
    void argumentOptionOfTheEndpointReplacesTheSameOptionOfTheComponent() {
        assertThat(findSpec("requiresLocale").getArguments()).containsExactly(
                new AiPromptArgument("locale", "The locale", true));
        // the component configuration is copied, so the other prompts keep the argument optional
        assertThat(findSpec("componentOnly").getArguments()).containsExactly(
                new AiPromptArgument("locale", "The locale", false));
    }

    private AiPromptSpec findSpec(String promptName) {
        return AiPromptRegistry.getOrCreate(context).getPromptsByTag("arguments").stream()
                .filter(prompt -> promptName.equals(prompt.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Prompt not found: " + promptName));
    }
}
