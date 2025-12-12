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

public class LangChain4jToolTest extends CamelTestSupport {

    protected final String nameFromDB = "pippo";
    protected ChatModel chatModel;

    @RegisterExtension
    static OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("What is the name of the user 1?\n")
            .invokeTool("QueryUserByNumber")
            .withParam("number", 1)
            .end()
            .when("What tools are available for working with users?\n")
            .replyWith("The following tools are available for working with users: queryUserById, queryUserBySSN, sendEmail")
            .end()
            .when("Get the user with ID 42.\n")
            .invokeTool("queryUserById")
            .withParam("userId", 42)
            .end()
            .when("I need to query a user by their social security number. First, find the appropriate tool, then use it to query SSN 123-45-6789.\n")
            .invokeTool("toolSearchTool")
            .withParam("tags", "users")
            .andThenInvokeTool("queryUserBySSN")
            .withParam("ssn", "123-45-6789")
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

                from("langchain4j-tools:test1?tags=user&name=QueryUserByNumber&description=Query user database by number&parameter.number=integer")
                        .setBody(simple("{\"name\": \"pippo\"}"));

                from("langchain4j-tools:test1?tags=user&description=Does not really do anything")
                        .setBody(constant("Hello World"));

                from("langchain4j-tools:test1?tags=user&name=DoesNothing&description=Also does not really do anything, but has a name")
                        .setBody(constant("Hello World"));

                // Search tool test routes
                from("direct:searchToolTest")
                        .to("langchain4j-tools:test1?tags=users,products");

                // Exposed tool - should be immediately available to LLM
                from("langchain4j-tools:queryUserById?tags=users&description=Query user database by user ID&parameter.userId=integer")
                        .setBody(simple("{\"name\": \"John Doe\", \"id\": ${header.userId}}"));

                // Non-exposed (searchable) tool - only available via tool search
                from("langchain4j-tools:queryUserBySSN?tags=users&description=Query user database by social security number&parameter.ssn=string&exposed=false")
                        .setBody(simple("{\"name\": \"Jane Smith\", \"ssn\": \"${header.ssn}\"}"));

                // Another non-exposed tool with different tag
                from("langchain4j-tools:queryProductById?tags=products&description=Query product database by product ID&parameter.productId=integer&exposed=false")
                        .setBody(simple("{\"product\": \"Widget\", \"id\": ${header.productId}}"));

                // Non-exposed tool for email operations
                from("langchain4j-tools:sendEmail?tags=users&description=Send email to a user&parameter.email=string&parameter.message=string&exposed=false")
                        .setBody(simple("Email sent to ${header.email}: ${header.message}"));
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

        Exchange exchange = fluentTemplate.to("direct:test").withBody(messages).request(Exchange.class);

        Assertions.assertThat(exchange).isNotNull();
        Message message = exchange.getMessage();
        Assertions.assertThat(message.getBody(String.class)).containsIgnoringCase(nameFromDB);
        Assertions.assertThat(message.getHeader("number")).isInstanceOf(Integer.class);
    }

    @Test
    public void testToolSearchForUsers() throws Exception {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("""
                You are a helpful assistant with access to various tools.
                If you need to find available tools, use the toolSearchTool to search by tags.
                """));
        messages.add(new UserMessage("""
                What tools are available for working with users?
                """));

        Exchange result = fluentTemplate.to("direct:searchToolTest").withBody(messages).request(Exchange.class);

        Assertions.assertThat(result).isNotNull();
        String response = result.getMessage().getBody(String.class);
        Assertions.assertThat(response).isNotNull();
    }

    @Test
    public void testExposedToolDirectAccess() throws Exception {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("""
                You are a helpful assistant that can query user information.
                """));
        messages.add(new UserMessage("""
                Get the user with ID 42.
                """));

        Exchange result = fluentTemplate.to("direct:searchToolTest").withBody(messages).request(Exchange.class);

        Assertions.assertThat(result).isNotNull();
        String response = result.getMessage().getBody(String.class);
        Assertions.assertThat(response).isNotNull();
    }

    @Test
    public void testSearchAndUseNonExposedTool() throws Exception {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("""
                You are a helpful assistant. If you need to find tools, search for them first using the toolSearchTool.
                """));
        messages.add(new UserMessage(
                """
                        I need to query a user by their social security number. First, find the appropriate tool, then use it to query SSN 123-45-6789.
                        """));

        Exchange result = fluentTemplate.to("direct:searchToolTest").withBody(messages).request(Exchange.class);

        Assertions.assertThat(result).isNotNull();
        String response = result.getMessage().getBody(String.class);
        Assertions.assertThat(response).isNotNull();
    }
}
