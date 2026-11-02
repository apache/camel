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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.observability.GenAiObservabilityProperties;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks what the producer sends to the model for the different message bodies, with a fake {@link ChatModel} that
 * records the prompt it gets.
 */
class SpringAiChatBodyHandlingTest extends CamelTestSupport {

    private static final byte[] PNG_BYTES = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 0x0d };

    private final RecordingChatModel chatModel = new RecordingChatModel();

    @TempDir
    Path tempDir;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        Properties properties = new Properties();
        properties.setProperty(GenAiObservabilityProperties.ENABLED, "false");
        context.getPropertiesComponent().setOverrideProperties(properties);
        context.getRegistry().bind("chatModel", chatModel);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:chat")
                        .to("spring-ai-chat:chat?chatModel=#chatModel");

                from("direct:list")
                        .to("spring-ai-chat:list?chatModel=#chatModel&outputFormat=LIST");

                from("direct:map")
                        .to("spring-ai-chat:map?chatModel=#chatModel&outputFormat=MAP");

                from("direct:multiple")
                        .to("spring-ai-chat:multiple?chatModel=#chatModel&chatOperation=CHAT_MULTIPLE_MESSAGES");

                from("direct:multipleList")
                        .to("spring-ai-chat:multipleList?chatModel=#chatModel&chatOperation=CHAT_MULTIPLE_MESSAGES"
                            + "&outputFormat=LIST");
            }
        };
    }

    @Test
    void outputFormatListKeepsTheQuestion() {
        chatModel.answer("red, green, blue");

        Object body = template.requestBody("direct:list", "Name three colors", Object.class);

        assertThat(body).isEqualTo(List.of("red", "green", "blue"));
        UserMessage user = chatModel.lastUserMessage();
        assertThat(user.getText()).startsWith("Name three colors").contains("comma-separated");
        assertThat(chatModel.lastPrompt().getInstructions()).hasSize(1);
    }

    @Test
    void outputFormatMapKeepsTheQuestion() {
        chatModel.answer("{\"capital\": \"Paris\"}");

        Object body = template.requestBody("direct:map", "What is the capital of France?", Object.class);

        assertThat(body).isEqualTo(Map.of("capital", "Paris"));
        UserMessage user = chatModel.lastUserMessage();
        assertThat(user.getText()).startsWith("What is the capital of France?").contains("JSON");
    }

    @Test
    void outputFormatListAppendsTheFormatToTheLastUserMessageOfAConversation() {
        chatModel.answer("one, two");

        List<Message> conversation = List.of(
                new UserMessage("Hello"),
                new AssistantMessage("Hi"),
                new UserMessage("Count to two"));
        Object body = template.requestBody("direct:multipleList", conversation, Object.class);

        assertThat(body).isEqualTo(List.of("one", "two"));
        List<Message> sent = chatModel.lastPrompt().getInstructions();
        assertThat(sent).hasSize(3);
        assertThat(sent.get(0).getText()).isEqualTo("Hello");
        assertThat(sent.get(2).getText()).startsWith("Count to two").contains("comma-separated");
    }

    @Test
    void mediaBodyWithoutUserMessageFailsWithAClearMessage() {
        assertThatThrownBy(() -> template.requestBody("direct:chat", PNG_BYTES, String.class))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userMessage")
                .hasMessageContaining(SpringAiChatConstants.USER_MESSAGE);
        assertThat(chatModel.lastPrompt()).isNull();
    }

    @Test
    void mediaBodyWithUserMessageHeader() {
        String answer = template.requestBodyAndHeader("direct:chat", PNG_BYTES,
                SpringAiChatConstants.USER_MESSAGE, "What is in this image?", String.class);

        assertThat(answer).isEqualTo("ok");
        UserMessage user = chatModel.lastUserMessage();
        assertThat(user.getText()).isEqualTo("What is in this image?");
        assertThat(user.getMedia()).singleElement()
                .satisfies(media -> assertThat(media.getMimeType().toString()).isEqualTo("image/png"));
    }

    @Test
    void fileBodyIsSentAsMedia() throws Exception {
        File image = tempDir.resolve("photo.png").toFile();
        Files.write(image.toPath(), PNG_BYTES);

        String answer = template.requestBodyAndHeader("direct:chat", image,
                SpringAiChatConstants.USER_MESSAGE, "What is in this image?", String.class);

        assertThat(answer).isEqualTo("ok");
        UserMessage user = chatModel.lastUserMessage();
        assertThat(user.getText()).isEqualTo("What is in this image?");
        assertThat(user.getMedia()).singleElement().satisfies(media -> {
            assertThat(media.getMimeType().toString()).isEqualTo("image/png");
            assertThat(media.getDataAsByteArray()).isEqualTo(PNG_BYTES);
        });
    }

    @Test
    void pathBodyIsSentAsMedia() throws Exception {
        Path image = tempDir.resolve("photo.jpg");
        Files.write(image, PNG_BYTES);

        template.requestBodyAndHeader("direct:chat", image,
                SpringAiChatConstants.USER_MESSAGE, "What is in this image?", String.class);

        assertThat(chatModel.lastUserMessage().getMedia()).singleElement()
                .satisfies(media -> assertThat(media.getMimeType().toString()).isEqualTo("image/jpeg"));
    }

    @Test
    void textFileBodyIsStillSentAsText() throws Exception {
        File prompt = tempDir.resolve("prompt.txt").toFile();
        Files.writeString(prompt.toPath(), "What is Apache Camel?");

        template.requestBody("direct:chat", prompt, String.class);

        UserMessage user = chatModel.lastUserMessage();
        assertThat(user.getText()).isEqualTo("What is Apache Camel?");
        assertThat(user.getMedia()).isEmpty();
    }

    @Test
    void streamBodyWithMediaTypeIsSentAsMedia() {
        InputStream stream = new ByteArrayInputStream(PNG_BYTES);

        template.requestBodyAndHeaders("direct:chat", stream,
                Map.of(SpringAiChatConstants.USER_MESSAGE, "What is in this image?",
                        SpringAiChatConstants.MEDIA_TYPE, "image/png"),
                String.class);

        UserMessage user = chatModel.lastUserMessage();
        assertThat(user.getText()).isEqualTo("What is in this image?");
        assertThat(user.getMedia()).singleElement().satisfies(media -> {
            assertThat(media.getMimeType().toString()).isEqualTo("image/png");
            assertThat(media.getDataAsByteArray()).isEqualTo(PNG_BYTES);
        });
    }

    @Test
    void streamBodyWithImageFileNameIsSentAsMedia() {
        InputStream stream = new ByteArrayInputStream(PNG_BYTES);

        template.requestBodyAndHeaders("direct:chat", stream,
                Map.of(SpringAiChatConstants.USER_MESSAGE, "What is in this image?",
                        Exchange.FILE_NAME, "inbox/photo.png"),
                String.class);

        assertThat(chatModel.lastUserMessage().getMedia()).singleElement()
                .satisfies(media -> assertThat(media.getMimeType().toString()).isEqualTo("image/png"));
    }

    @Test
    void streamBodyLargerThanMaxFileSizeFails() {
        InputStream stream = new ByteArrayInputStream(PNG_BYTES);

        assertThatThrownBy(() -> template.requestBodyAndHeaders("direct:chat", stream,
                Map.of(SpringAiChatConstants.USER_MESSAGE, "What is in this image?",
                        SpringAiChatConstants.MEDIA_TYPE, "image/png",
                        SpringAiChatConstants.MAX_FILE_SIZE, 4L),
                String.class))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum allowed size (4 bytes)");
        assertThat(chatModel.lastPrompt()).isNull();
    }

    @Test
    void textStreamBodyIsStillSentAsText() {
        InputStream stream = new ByteArrayInputStream("What is Apache Camel?".getBytes(StandardCharsets.UTF_8));

        template.requestBodyAndHeader("direct:chat", stream, Exchange.CONTENT_TYPE, "text/plain; charset=UTF-8",
                String.class);

        UserMessage user = chatModel.lastUserMessage();
        assertThat(user.getText()).isEqualTo("What is Apache Camel?");
        assertThat(user.getMedia()).isEmpty();
    }

    @Test
    void streamBodyWithOctetStreamContentTypeIsStillSentAsText() {
        InputStream stream = new ByteArrayInputStream("What is Apache Camel?".getBytes(StandardCharsets.UTF_8));

        template.requestBodyAndHeader("direct:chat", stream, Exchange.CONTENT_TYPE, "application/octet-stream",
                String.class);

        UserMessage user = chatModel.lastUserMessage();
        assertThat(user.getText()).isEqualTo("What is Apache Camel?");
        assertThat(user.getMedia()).isEmpty();
    }

    @Test
    void multipleMessagesTakesStrings() {
        template.requestBody("direct:multiple", List.of("Hello", "What is Apache Camel?"), String.class);

        List<Message> sent = chatModel.lastPrompt().getInstructions();
        assertThat(sent).hasSize(2).allSatisfy(message -> assertThat(message).isInstanceOf(UserMessage.class));
        assertThat(sent.get(0).getText()).isEqualTo("Hello");
        assertThat(sent.get(1).getText()).isEqualTo("What is Apache Camel?");
    }

    @Test
    void multipleMessagesTakesMessagesAndStrings() {
        template.requestBody("direct:multiple",
                List.of(new SystemMessage("Be brief"), "What is Apache Camel?"), String.class);

        List<Message> sent = chatModel.lastPrompt().getInstructions();
        assertThat(sent).hasSize(2);
        assertThat(sent.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(sent.get(1)).isInstanceOf(UserMessage.class);
        assertThat(sent.get(1).getText()).isEqualTo("What is Apache Camel?");
    }

    @Test
    void multipleMessagesWithOtherElementFailsWithAClearMessage() {
        assertThatThrownBy(() -> template.requestBody("direct:multiple", List.of(42), String.class))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("java.lang.Integer")
                .hasMessageContaining("org.springframework.ai.chat.messages.Message");
        assertThat(chatModel.lastPrompt()).isNull();
    }

    /**
     * A fake model that records the prompt and answers with a preset text.
     */
    private static final class RecordingChatModel implements ChatModel {

        private final AtomicReference<Prompt> lastPrompt = new AtomicReference<>();
        private volatile String answer = "ok";

        void answer(String answer) {
            this.answer = answer;
        }

        Prompt lastPrompt() {
            return lastPrompt.get();
        }

        UserMessage lastUserMessage() {
            return lastPrompt.get().getUserMessage();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            lastPrompt.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
        }
    }
}
