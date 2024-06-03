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
package org.apache.camel.component.langchain4j.chat;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.chat.tool.CamelSimpleToolParameter;
import org.apache.camel.component.langchain4j.chat.tool.NamedJsonSchemaProperty;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class LangChain4jConsumerIT extends CamelTestSupport {

    private final String nameFromDB = "pippo";

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        LangChain4jChatComponent component
                = context.getComponent(LangChain4jChat.SCHEME, LangChain4jChatComponent.class);

        component.getConfiguration().setChatModel(
                OpenAiChatModel.withApiKey(System.getenv("OPENAI_API_KEY")));

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                NamedJsonSchemaProperty namedJsonSchemaProperty
                        = new NamedJsonSchemaProperty("name", List.of(JsonSchemaProperty.STRING));
                CamelSimpleToolParameter camelToolParameter = new CamelSimpleToolParameter(
                        "This is a tool description",
                        List.of(namedJsonSchemaProperty));
                context().getRegistry().bind("parameters", camelToolParameter);

                from("direct:test")
                        .to("langchain4j-chat:test1?chatOperation=CHAT_MULTIPLE_MESSAGES");

                from("langchain4j-chat:test1?description=Query user database by number&parameter.number=integer")
                        .process(exchange -> exchange.getIn().setBody(nameFromDB));

                from("langchain4j-chat:test1?camelToolParameter=#parameters")
                        .setBody(constant("Hello World"));
            }
        };
    }

    @Test
    public void testSimpleInvocation() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("""
                You provide information about specific user name querying the database given a number.
                """));
        messages.add(new UserMessage("""
                What is the name of the user 1?
                """));

        Exchange message = fluentTemplate.to("direct:test").withBody(messages).request(Exchange.class);

        Assertions.assertTrue(message.getMessage().getBody().toString().contains(nameFromDB));
    }
}
