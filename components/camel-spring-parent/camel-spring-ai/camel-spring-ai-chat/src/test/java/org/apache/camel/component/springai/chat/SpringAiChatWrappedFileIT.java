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

import java.net.URL;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for WrappedFile support in multimodal capabilities.
 *
 * Tests the ability to use files directly from the file component with spring-ai-chat. This enables patterns like:
 * from("file:...").to("spring-ai-chat:...")
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
@Disabled("TODO: Find a model that support images and pdf")
public class SpringAiChatWrappedFileIT extends OllamaTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SpringAiChatWrappedFileIT.class);

    private String getTestResourcePath(String resource) {
        URL url = getClass().getClassLoader().getResource(resource);
        if (url == null) {
            throw new RuntimeException("Resource not found: " + resource);
        }
        return url.getPath();
    }

    @Override
    protected String modelName() {
        return "qwen3-vl:2b";
    }

    @Override
    protected org.springframework.ai.chat.model.ChatModel createModel() {
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(OLLAMA.baseUrl())
                .build();

        OllamaChatOptions ollamaOptions
                = OllamaChatOptions.builder()
                        .model(modelName())
                        .temperature(0.3)
                        .build();

        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(ollamaOptions)
                .retryTemplate(RetryTemplate.builder()
                        .maxAttempts(1)
                        .build())
                .build();
    }

    @Test
    public void testWrappedFileWithAutoDetectedMimeType() throws Exception {
        String response = template().requestBody("direct:auto-detect", null, String.class);
        LOG.info("LLM Response (auto-detected MIME): {}", response);
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
    }

    @Test
    public void testWrappedFileWithHeaderSpecifiedMimeType() throws Exception {
        String response = template().requestBody("direct:header-mime", null, String.class);
        LOG.info("LLM Response (header-specified MIME): {}", response);
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
    }

    @Test
    public void testWrappedFileWithConfiguredUserMessage() throws Exception {
        String response = template().requestBody("direct:configured", null, String.class);
        LOG.info("LLM Response (configured user message): {}", response);
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
    }

    @Test
    public void testWrappedFileWithUserMessageHeader() throws Exception {
        String response = template().requestBody("direct:header-msg", null, String.class);
        LOG.info("LLM Response (user message header): {}", response);
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
    }

    @Test
    public void testWrappedFileWithFileContentTypeHeader() throws Exception {
        String response = template().requestBody("direct:content-type", null, String.class);
        LOG.info("LLM Response (FILE_CONTENT_TYPE header): {}", response);
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                bindChatModel(this.getCamelContext());

                String testFilesPath = getTestResourcePath("test-files");

                // Route 1: Auto-detect MIME type from file extension (PNG)
                from("direct:auto-detect")
                        .pollEnrich("file:" + testFilesPath + "?fileName=test-image.png&noop=true&idempotent=false")
                        .setHeader(SpringAiChatConstants.USER_MESSAGE, constant("What color is this image?"))
                        .to("spring-ai-chat:wrapped-file?chatModel=#chatModel");

                // Route 2: Header-specified MIME type (JPEG)
                from("direct:header-mime")
                        .pollEnrich("file:" + testFilesPath + "?fileName=test-image.jpg&noop=true&idempotent=false")
                        .setHeader(SpringAiChatConstants.USER_MESSAGE, constant("Describe this image."))
                        .setHeader(SpringAiChatConstants.MEDIA_TYPE, constant("image/jpeg"))
                        .to("spring-ai-chat:wrapped-file-header?chatModel=#chatModel");

                // Route 3: Configured user message on endpoint
                from("direct:configured")
                        .pollEnrich("file:" + testFilesPath + "?fileName=test-image.png&noop=true&idempotent=false")
                        .to("spring-ai-chat:wrapped-file-config?chatModel=#chatModel&userMessage=Analyze this image in detail.");

                // Route 4: User message from header
                from("direct:header-msg")
                        .pollEnrich("file:" + testFilesPath + "?fileName=test-image.png&noop=true&idempotent=false")
                        .setHeader(SpringAiChatConstants.USER_MESSAGE, constant("What do you see in this picture?"))
                        .to("spring-ai-chat:wrapped-file-msg?chatModel=#chatModel");

                // Route 5: PDF file auto-detection
                from("direct:pdf")
                        .pollEnrich("file:" + testFilesPath + "?fileName=test-document.pdf&noop=true&idempotent=false")
                        .setHeader(SpringAiChatConstants.USER_MESSAGE,
                                constant("Summarize the content of this PDF document."))
                        .to("spring-ai-chat:wrapped-file-pdf?chatModel=#chatModel");

                // Route 6: FILE_CONTENT_TYPE header from file component
                from("direct:content-type")
                        .pollEnrich("file:" + testFilesPath + "?fileName=test-image.png&noop=true&idempotent=false")
                        .setHeader(SpringAiChatConstants.USER_MESSAGE, constant("Describe this image."))
                        // Simulate FILE_CONTENT_TYPE header that would be set by file component with probeContentType=true
                        .setHeader(org.apache.camel.Exchange.FILE_CONTENT_TYPE, constant("image/png"))
                        .to("spring-ai-chat:wrapped-file-ct?chatModel=#chatModel");
            }
        };
    }
}
