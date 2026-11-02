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
package org.apache.camel.component.langchain4j.agent;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AgentWithMemory;
import org.apache.camel.component.langchain4j.agent.api.AgentWithoutMemory;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.apache.camel.component.langchain4j.agent.api.Headers;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the user message the chat model receives for a file, bytes, stream or {@link AiAgentBody} body with a content.
 */
class LangChain4jAgentContentBodyTest extends CamelTestSupport {

    @TempDir
    Path tempDir;

    private final CapturingChatModel chatModel = new CapturingChatModel();

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("agent", new AgentWithoutMemory(new AgentConfiguration().withChatModel(chatModel)));
        registry.bind("memoryAgent", new AgentWithMemory(
                new AgentConfiguration()
                        .withChatModel(chatModel)
                        .withChatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:agent").to("langchain4j-agent:test?agent=#agent");
                from("direct:memory").to("langchain4j-agent:memory?agent=#memoryAgent");
            }
        };
    }

    @BeforeEach
    void reset() {
        chatModel.requests.clear();
    }

    @Test
    void textFileWithoutUserMessageSendsTheContentOnce() throws Exception {
        File file = Files.writeString(tempDir.resolve("notes.txt"), "Hello file").toFile();
        GenericFile<File> genericFile = new GenericFile<>();
        genericFile.setFile(file);
        genericFile.setFileName(file.getName());

        String reply = template.requestBody("direct:agent", genericFile, String.class);

        assertThat(reply).isEqualTo("ok");
        assertThat(lastUserMessageContents()).singleElement()
                .isInstanceOfSatisfying(TextContent.class, c -> assertThat(c.text()).isEqualTo("Hello file"));
    }

    @Test
    void textBytesWithoutUserMessageSendsTheContentOnce() {
        template.requestBodyAndHeader("direct:agent", "Hello bytes".getBytes(StandardCharsets.UTF_8),
                Headers.MEDIA_TYPE, "text/plain", String.class);

        assertThat(lastUserMessageContents()).singleElement()
                .isInstanceOfSatisfying(TextContent.class, c -> assertThat(c.text()).isEqualTo("Hello bytes"));
    }

    @Test
    void textStreamWithoutUserMessageSendsTheContentOnceWithMemory() {
        template.requestBodyAndHeaders("direct:memory",
                new ByteArrayInputStream("Hello stream".getBytes(StandardCharsets.UTF_8)),
                Map.of(Headers.MEDIA_TYPE, "text/plain", Headers.MEMORY_ID, "m1"), String.class);

        assertThat(lastUserMessageContents()).singleElement()
                .isInstanceOfSatisfying(TextContent.class, c -> assertThat(c.text()).isEqualTo("Hello stream"));
    }

    @Test
    void imageBytesWithoutUserMessageSendsOnlyTheImage() {
        template.requestBodyAndHeader("direct:agent", new byte[] { 1, 2, 3 }, Headers.MEDIA_TYPE, "image/png",
                String.class);

        assertThat(lastUserMessageContents()).singleElement().isInstanceOf(ImageContent.class);
    }

    @Test
    void textBytesWithSystemMessageAndWithoutUserMessageSendsTheContentOnce() {
        template.requestBodyAndHeaders("direct:agent", "Hello bytes".getBytes(StandardCharsets.UTF_8),
                Map.of(Headers.MEDIA_TYPE, "text/plain", Headers.SYSTEM_MESSAGE, "Be brief"), String.class);

        List<ChatMessage> messages = chatModel.requests.get(chatModel.requests.size() - 1).messages();
        assertThat(messages.get(0)).isInstanceOfSatisfying(SystemMessage.class,
                m -> assertThat(m.text()).isEqualTo("Be brief"));
        assertThat(lastUserMessageContents()).singleElement()
                .isInstanceOfSatisfying(TextContent.class, c -> assertThat(c.text()).isEqualTo("Hello bytes"));
    }

    @Test
    void textFileWithUserMessageSendsTextAndContent() throws Exception {
        File file = Files.writeString(tempDir.resolve("notes.txt"), "Hello file").toFile();
        GenericFile<File> genericFile = new GenericFile<>();
        genericFile.setFile(file);
        genericFile.setFileName(file.getName());

        template.requestBodyAndHeader("direct:agent", genericFile, Headers.USER_MESSAGE, "Summarize", String.class);

        assertThat(lastUserMessageContents()).hasSize(2);
        assertThat(((TextContent) lastUserMessageContents().get(0)).text()).isEqualTo("Summarize");
        assertThat(((TextContent) lastUserMessageContents().get(1)).text()).isEqualTo("Hello file");
    }

    @Test
    void blankUserMessageWithContentSendsOnlyTheContent() {
        AiAgentBody<TextContent> body = new AiAgentBody<TextContent>(" ")
                .withContent(TextContent.from("Hello content"));

        String reply = template.requestBody("direct:agent", body, String.class);

        assertThat(reply).isEqualTo("ok");
        assertThat(lastUserMessageContents()).singleElement()
                .isInstanceOfSatisfying(TextContent.class, c -> assertThat(c.text()).isEqualTo("Hello content"));
    }

    @Test
    void blankUserMessageWithContentSendsOnlyTheContentWithMemory() {
        AiAgentBody<TextContent> body = new AiAgentBody<TextContent>(" ")
                .withContent(TextContent.from("Hello content"))
                .withMemoryId("m2");

        String reply = template.requestBody("direct:memory", body, String.class);

        assertThat(reply).isEqualTo("ok");
        assertThat(lastUserMessageContents()).singleElement()
                .isInstanceOfSatisfying(TextContent.class, c -> assertThat(c.text()).isEqualTo("Hello content"));
    }

    @Test
    void jsonFileIsSentAsText() throws Exception {
        File file = Files.writeString(tempDir.resolve("order.json"), "{\"id\":1}").toFile();
        GenericFile<File> genericFile = new GenericFile<>();
        genericFile.setFile(file);
        genericFile.setFileName(file.getName());

        template.requestBodyAndHeader("direct:agent", genericFile, Headers.USER_MESSAGE, "Check", String.class);

        assertThat(lastUserMessageContents()).hasSize(2);
        assertThat(((TextContent) lastUserMessageContents().get(1)).text()).isEqualTo("{\"id\":1}");
    }

    @Test
    void jsonAndXmlBytesAreSentAsText() {
        template.requestBodyAndHeader("direct:agent", "{\"id\":2}".getBytes(StandardCharsets.UTF_8),
                Exchange.CONTENT_TYPE, "application/json; charset=UTF-8", String.class);
        assertThat(lastUserMessageContents()).singleElement()
                .isInstanceOfSatisfying(TextContent.class, c -> assertThat(c.text()).isEqualTo("{\"id\":2}"));

        template.requestBodyAndHeader("direct:agent", "<id>3</id>".getBytes(StandardCharsets.UTF_8),
                Exchange.CONTENT_TYPE, "Application/XML", String.class);
        assertThat(lastUserMessageContents()).singleElement()
                .isInstanceOfSatisfying(TextContent.class, c -> assertThat(c.text()).isEqualTo("<id>3</id>"));

        template.requestBodyAndHeader("direct:agent", "{\"id\":4}".getBytes(StandardCharsets.UTF_8),
                Exchange.CONTENT_TYPE, "application/vnd.api+json", String.class);
        assertThat(lastUserMessageContents()).singleElement()
                .isInstanceOfSatisfying(TextContent.class, c -> assertThat(c.text()).isEqualTo("{\"id\":4}"));
    }

    @Test
    void fileBodyIsConverted() throws Exception {
        File file = Files.writeString(tempDir.resolve("plain.txt"), "Hello java.io.File").toFile();

        template.requestBodyAndHeader("direct:agent", file, Headers.USER_MESSAGE, "Read", String.class);

        assertThat(lastUserMessageContents()).hasSize(2);
        assertThat(((TextContent) lastUserMessageContents().get(1)).text()).isEqualTo("Hello java.io.File");
    }

    @Test
    void pathBodyIsConverted() throws Exception {
        Path path = Files.writeString(tempDir.resolve("data.json"), "{\"path\":true}");

        template.requestBody("direct:agent", path, String.class);

        assertThat(lastUserMessageContents()).singleElement()
                .isInstanceOfSatisfying(TextContent.class, c -> assertThat(c.text()).isEqualTo("{\"path\":true}"));
    }

    private List<Content> lastUserMessageContents() {
        assertThat(chatModel.requests).isNotEmpty();
        List<ChatMessage> messages = chatModel.requests.get(chatModel.requests.size() - 1).messages();
        ChatMessage last = messages.get(messages.size() - 1);
        assertThat(last).isInstanceOf(UserMessage.class);
        return ((UserMessage) last).contents();
    }

    private static final class CapturingChatModel implements ChatModel {

        private final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public ChatResponse doChat(ChatRequest request) {
            requests.add(request);
            return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
        }
    }
}
