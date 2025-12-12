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

public class LangChain4jToolMultipleGroupsTest extends CamelTestSupport {

    private final String nameFromDB = "pippo";
    protected ChatModel chatModel;

    @RegisterExtension
    static OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("What is the name of the user 1?\n")
            .assertRequest(request -> {
                // The tools has to be included in the request
                Assertions.assertThat(request).contains(
                        "QueryUserDatabaseByNumber",
                        "DoesNotReallyDoAnything");

                // The NOT included in the request
                Assertions.assertThat(request).doesNotContain(
                        "QueryCompanyUserDatabaseByNumber",
                        "QuerySomethingelseUserDatabaseByNumber");
            })
            .invokeTool("{\"name\": \"pippo\"}")
            .withParam("number", "1")
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
                        .to("langchain4j-tools:test1?tags=user")
                        .log("response is: ${body}");

                from("langchain4j-tools:test1?tags=user&description=Query user database by number&parameter.number=integer")
                        .setBody(simple("{\"name\": \"pippo\"}"));

                from("langchain4j-tools:test1?tags=user&description=Does not really do anything")
                        .setBody(constant("Hello World"));

                from("langchain4j-tools:test1?tags=companies&description=Query company user database by number&parameter.number=integer")
                        .setBody(constant("Hello World"));

                from("langchain4j-tools:test1?tags=somethingelse&description=Query somethingelse user database by number&parameter.number=integer")
                        .setBody(constant("Hello World"));
            }
        };
    }

    @Test
    public void testSimpleInvocation() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(
                """
                        You provide the requested information using the functions you hava available. You can invoke the functions to obtain the information you need to complete the answer.
                        """));
        messages.add(new UserMessage("""
                What is the name of the user 1?
                """));

        Exchange message = fluentTemplate.to("direct:test").withBody(messages).request(Exchange.class);

        Assertions.assertThat(message).isNotNull();
        final String responseContent = message.getMessage().getBody().toString();
        Assertions.assertThat(responseContent).containsIgnoringCase(nameFromDB);
    }
}
