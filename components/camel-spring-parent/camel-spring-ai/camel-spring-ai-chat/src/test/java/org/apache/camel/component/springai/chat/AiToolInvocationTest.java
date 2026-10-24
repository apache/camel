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
package org.apache.camel.component.springai.chat;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.tool.AiToolConstants;
import org.apache.camel.component.ai.tool.AiToolRegistry;
import org.apache.camel.component.ai.tool.AiToolSpec;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;

class AiToolInvocationTest extends CamelTestSupport {

    private final AtomicReference<Exchange> toolExchange = new AtomicReference<>();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:weather?tags=test&parameter.city=string&argumentsAsBody=true")
                        .setProperty("inputBody", body())
                        .process(toolExchange::set)
                        .setBody(constant("sunny"));
            }
        };
    }

    @Test
    void propagatesConversationIdAndJsonArguments() throws Exception {
        Exchange caller = new DefaultExchange(context);
        caller.getMessage().setHeader(SpringAiChatConstants.CONVERSATION_ID, "conversation-123");
        ToolCallback callback = AiToolSpecToSpringAi.toToolCallback(spec(), caller);

        assertThat(callback.call("{\"city\":\"Rome\"}")).isEqualTo("\"sunny\"");
        assertThat(toolExchange.get().getMessage().getHeader(AiToolConstants.CONVERSATION_ID))
                .isEqualTo("conversation-123");
        assertThat(toolExchange.get().getMessage().getHeader(AiToolConstants.TOOL_CALL_ID)).isNull();
        assertThat(toolExchange.get().getMessage().getHeader("city")).isEqualTo("Rome");
        assertThat(Jsoner.deserialize(toolExchange.get().getProperty("inputBody", String.class)))
                .isEqualTo(Jsoner.deserialize("{\"city\":\"Rome\"}"));
    }

    @Test
    void omitsUnavailableIdentifiers() {
        ToolCallback callback = AiToolSpecToSpringAi.toToolCallback(spec(), new DefaultExchange(context));

        assertThat(callback.call("{\"city\":\"Rome\"}")).isEqualTo("\"sunny\"");
        assertThat(toolExchange.get().getMessage().getHeaders()).doesNotContainKeys(
                AiToolConstants.TOOL_CALL_ID, AiToolConstants.CONVERSATION_ID);
    }

    private AiToolSpec spec() {
        return AiToolRegistry.getOrCreate(context).getToolsByTag("test").iterator().next();
    }
}
