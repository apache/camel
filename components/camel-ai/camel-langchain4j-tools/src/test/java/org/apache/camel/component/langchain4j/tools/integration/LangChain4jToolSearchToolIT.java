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
package org.apache.camel.component.langchain4j.tools.integration;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test for the native ToolSearchTool functionality using Ollama
 */
@EnabledIfEnvironmentVariable(named = "OLLAMA_BASE_URL", matches = ".*")
public class LangChain4jToolSearchToolIT extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(LangChain4jToolSearchToolIT.class);

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                ChatModel model = OllamaChatModel.builder()
                        .baseUrl(System.getenv("OLLAMA_BASE_URL"))
                        .modelName("llama3.1:latest")
                        .temperature(0.0)
                        .timeout(ofSeconds(60000))
                        .build();
                context.getRegistry().bind("chatModel", model);

                // Producer route
                from("direct:test")
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
    void testToolSearchForUsers() throws Exception {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("""
                You are a helpful assistant with access to various tools.
                If you need to find available tools, use the toolSearchTool to search by tags.
                """));
        messages.add(new UserMessage("""
                What tools are available for working with users?
                """));

        Exchange result = fluentTemplate.to("direct:test").withBody(messages).request(Exchange.class);

        assertNotNull(result, "An Exchange is expected.");
        String response = result.getMessage().getBody(String.class);
        assertNotNull(response, "A response is expected.");
        LOG.info("Response: {}", response);
    }

    @Test
    void testExposedToolDirectAccess() throws Exception {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("""
                You are a helpful assistant that can query user information.
                """));
        messages.add(new UserMessage("""
                Get the user with ID 42.
                """));

        Exchange result = fluentTemplate.to("direct:test").withBody(messages).request(Exchange.class);

        assertNotNull(result, "An Exchange is expected.");
        String response = result.getMessage().getBody(String.class);
        assertNotNull(response, "A response is expected.");
        LOG.info("Response: {}", response);
    }

    @Test
    void testSearchAndUseNonExposedTool() throws Exception {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("""
                You are a helpful assistant. If you need to find tools, search for them first using the toolSearchTool.
                """));
        messages.add(new UserMessage(
                """
                        I need to query a user by their social security number. First, find the appropriate tool, then use it to query SSN 123-45-6789.
                        """));

        Exchange result = fluentTemplate.to("direct:test").withBody(messages).request(Exchange.class);

        assertNotNull(result, "An Exchange is expected.");
        String response = result.getMessage().getBody(String.class);
        assertNotNull(response, "A response is expected.");
        LOG.info("Response: {}", response);
    }
}
