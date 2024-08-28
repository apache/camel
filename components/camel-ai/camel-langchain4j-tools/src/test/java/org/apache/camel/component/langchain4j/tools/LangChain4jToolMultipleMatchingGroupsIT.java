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
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static java.time.Duration.ofSeconds;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class LangChain4jToolMultipleMatchingGroupsIT extends CamelTestSupport {

    public static final String MODEL_NAME = "llama3.1:latest";
    private final String nameFromDB = "Paul McFly";
    private ChatLanguageModel chatLanguageModel;

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createServiceWithConfiguration(() -> MODEL_NAME);

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        chatLanguageModel = createModel();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        LangChain4jToolsComponent component
                = context.getComponent(LangChain4jTools.SCHEME, LangChain4jToolsComponent.class);

        component.getConfiguration().setChatModel(chatLanguageModel);

        return context;
    }

    protected ChatLanguageModel createModel() {
        chatLanguageModel = OpenAiChatModel.builder()
                .apiKey("NO_API_KEY")
                .modelName(MODEL_NAME)
                .baseUrl(OLLAMA.getEndpoint())
                .temperature(0.0)
                .timeout(ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();

        return chatLanguageModel;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                from("direct:test")
                        .to("langchain4j-tools:test1?tags=internalDB,externalDB")
                        .log("response is: ${body}");

                from("langchain4j-tools:test2?tags=internalDB&description=Query user by number using the internal DB&parameter.number=integer")
                        .setBody(simple("{\"name\": \"Paul McFly\"}"));

                from("langchain4j-tools:test3?tags=notApplicable&description=Does not really do anything")
                        .setBody(constant("Hello World"));

                from("langchain4j-tools:test4?tags=externalDB&description=Query user information by number using the external DB&parameter.number=integer")
                        .setBody(simple("{\"name\": \"Paul McFly\", \"department\": \"engineering\"}"));

                from("langchain4j-tools:test5?tags=notApplicable&description=Query car information by plate&parameter.number=integer")
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
                What is the name of the user 1 and what department he is part of?
                """));

        Exchange message = fluentTemplate.to("direct:test").withBody(messages).request(Exchange.class);

        Assertions.assertThat(message).isNotNull();
        final String responseContent = message.getMessage().getBody().toString();
        Assertions.assertThat(responseContent).containsIgnoringCase(nameFromDB);
        Assertions.assertThat(responseContent).containsIgnoringCase("engineering");
    }
}
