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
package org.apache.camel.component.langchain4j.tools;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class LangChain4jToolNoToolsExistTest extends CamelTestSupport {

    protected ChatModel chatModel;

    @RegisterExtension
    static OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("How can you help? DO NOT CALL TOOLS.\n")
            .assertRequest(request -> {
                // The tools should not be included in the request
                Assertions.assertThat(request).doesNotContain(
                        "QueryUserDatabaseByNumber",
                        "DoesNotDoAnything,Really");
            })
            .replyWith("No tool is invoked")
            .build();

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        chatModel = ToolsHelper.createModel(openAIMock.getBaseUrl());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        LangChain4jToolsComponent component
                = context.getComponent(LangChain4jTools.SCHEME, LangChain4jToolsComponent.class);

        component.getConfiguration().setChatModel(chatModel);

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:test")
                        .to("langchain4j-tools:test1?tags=other")
                        .log("response is: ${body}");

                from("langchain4j-tools:test1?tags=user&description=Query user database by number&parameter.number=integer")
                        .setBody(simple("{\"name\": \"pippo\"}"));

                from("langchain4j-tools:test1?tags=user&description=Does not do anything, really")
                        .setBody(simple("{\"name\": \"pippo\"}"));

                from("direct:noResponse")
                        .log("there is no tool to be called for the request: ${body}")
                        .setBody(constant("There was no tool to be called"))
                        .to("mock:noResponse");

            }
        };
    }

    @Test
    public void testSimpleInvocation() throws InterruptedException {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(
                """
                        Your job is to help me test my code. When asked to call a tool you do not call anything.
                        """));
        messages.add(new UserMessage("""
                How can you help? DO NOT CALL TOOLS.
                """));

        Exchange message = fluentTemplate.to("direct:test").withBody(messages).request(Exchange.class);

        Assertions.assertThat(message).isNotNull();
        Assertions.assertThat(message.getMessage().getHeader(LangChain4jTools.NO_TOOLS_CALLED_HEADER)).isEqualTo(Boolean.TRUE);

    }
}
