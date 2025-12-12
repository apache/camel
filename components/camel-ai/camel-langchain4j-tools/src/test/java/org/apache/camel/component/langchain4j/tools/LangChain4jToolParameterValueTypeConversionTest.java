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
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LangChain4jToolParameterValueTypeConversionTest extends CamelTestSupport {
    protected ChatModel chatModel;

    @RegisterExtension
    static OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("A test user message\n")
            .invokeTool("TestTool")
            .withParam("int", 1)
            .withParam("intNumeric", 2)
            .withParam("long", Long.MIN_VALUE)
            .withParam("double", 1.0)
            .withParam("boolean", true)
            .withParam("string", "1")
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
                        .to("langchain4j-tools:test?tags=test")
                        .log("response is: ${body}");

                from("langchain4j-tools:test?tags=test&name=TestTool&description=Test Tool&parameter.int=integer&parameter.intNumeric=number&parameter.long=number&parameter.double=number&parameter.boolean=boolean&parameter.string=string")
                        .setBody(simple("{\"content\": \"fake response\"}"));
            }
        };
    }

    @Test
    void parameterValueTypeConversion() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(
                """
                        You provide the requested information using the functions you hava available. You can invoke the functions to obtain the information you need to complete the answer.
                        """));
        messages.add(new UserMessage("""
                A test user message
                """));

        Exchange exchange = fluentTemplate.to("direct:test").withBody(messages).request(Exchange.class);

        Assertions.assertThat(exchange).isNotNull();
        Message message = exchange.getMessage();
        Assertions.assertThat(message.getHeader("int")).isInstanceOf(Integer.class);
        Assertions.assertThat(message.getHeader("int")).isEqualTo(1);

        Assertions.assertThat(message.getHeader("intNumeric")).isInstanceOf(Integer.class);
        Assertions.assertThat(message.getHeader("intNumeric")).isEqualTo(2);

        Assertions.assertThat(message.getHeader("long")).isInstanceOf(Long.class);
        Assertions.assertThat(message.getHeader("long")).isEqualTo(Long.MIN_VALUE);

        Assertions.assertThat(message.getHeader("double")).isInstanceOf(Double.class);
        Assertions.assertThat(message.getHeader("double")).isEqualTo(1.0);

        Assertions.assertThat(message.getHeader("boolean")).isInstanceOf(Boolean.class);
        Assertions.assertThat(message.getHeader("boolean")).isEqualTo(true);

        Assertions.assertThat(message.getHeader("string")).isInstanceOf(String.class);
        Assertions.assertThat(message.getHeader("string")).isEqualTo("1");
    }
}
