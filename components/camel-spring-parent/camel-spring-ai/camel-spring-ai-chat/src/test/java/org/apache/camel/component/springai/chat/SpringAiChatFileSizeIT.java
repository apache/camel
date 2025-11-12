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

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for file size validation functionality.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiChatFileSizeIT extends OllamaTestSupport {

    private String getTestResourcePath(String resource) {
        URL url = getClass().getClassLoader().getResource(resource);
        if (url == null) {
            throw new RuntimeException("Resource not found: " + resource);
        }
        return url.getPath();
    }

    @Test
    public void testFileSizeWithinLimit() throws IOException {
        // test-image.png is 74 bytes - well within the 1MB default limit
        String testFilePath = getTestResourcePath("test-files/test-image.png");
        byte[] imageData = Files.readAllBytes(Paths.get(testFilePath));
        assertThat(imageData.length).isLessThan(1024 * 1024); // Less than 1MB

        var exchange = template().request("direct:file-size-limit", e -> {
            e.getIn().setBody(imageData);
            e.getIn().setHeader(SpringAiChatConstants.USER_MESSAGE, "What color is this image?");
            e.getIn().setHeader(SpringAiChatConstants.MEDIA_TYPE, "image/png");
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
    }

    @Test
    public void testFileSizeExceedsConfiguredLimit() throws IOException {
        // large-file.bin is 150 bytes - exceeds 100 byte limit configured on endpoint
        String testFilesPath = getTestResourcePath("test-files");

        assertThatThrownBy(() -> {
            template().requestBody("direct:file-size-small-limit", null, String.class);
        }).isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File size")
                .hasMessageContaining("exceeds maximum allowed size");
    }

    @Test
    public void testFileSizeHeaderOverridesConfiguration() throws IOException {
        // medium-file.bin is 50 bytes - exceeds endpoint config (40 bytes) but within header override (60 bytes)
        String testFilesPath = getTestResourcePath("test-files");

        var exchange = template().request("direct:file-size-header-override", e -> {
            e.getIn().setHeader(SpringAiChatConstants.USER_MESSAGE, "Process this file.");
            // Override the endpoint's 40 byte limit with a 60 byte limit via header
            e.getIn().setHeader(SpringAiChatConstants.MAX_FILE_SIZE, 60L);
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
    }

    @Test
    public void testFileSizeZeroMeansNoLimit() throws IOException {
        // large-file.bin is 150 bytes - would exceed normal limits but maxFileSize=0 disables checking
        String testFilesPath = getTestResourcePath("test-files");

        var exchange = template().request("direct:file-size-no-limit", e -> {
            e.getIn().setHeader(SpringAiChatConstants.USER_MESSAGE, "Describe this file.");
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
    }

    @Test
    public void testFileSizeValidationWithWrappedFile() throws IOException {
        // large-file.bin is 150 bytes - exceeds 100 byte limit
        String testFilesPath = getTestResourcePath("test-files");

        assertThatThrownBy(() -> {
            template().requestBody("direct:wrapped-file-size-limit", null, String.class);
        }).isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File size")
                .hasMessageContaining("exceeds maximum allowed size");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                bindChatModel(this.getCamelContext());

                String testFilesPath = getTestResourcePath("test-files");

                // Route 1: Default limit (1MB from configuration defaults)
                // Uses small test-image.png (74 bytes)
                from("direct:file-size-limit")
                        .to("spring-ai-chat:default?chatModel=#chatModel");

                // Route 2: Small limit (100 bytes) configured on endpoint
                // Uses large-file.bin (150 bytes) - should fail
                from("direct:file-size-small-limit")
                        .pollEnrich("file:" + testFilesPath + "?fileName=large-file.bin&noop=true&idempotent=false")
                        .setHeader(SpringAiChatConstants.USER_MESSAGE, constant("What is in this file?"))
                        .to("spring-ai-chat:small?chatModel=#chatModel&maxFileSize=100");

                // Route 3: Header override test
                // Uses medium-file.bin (50 bytes) - exceeds endpoint config (40 bytes) but header allows it (60 bytes)
                from("direct:file-size-header-override")
                        .pollEnrich("file:" + testFilesPath + "?fileName=medium-file.bin&noop=true&idempotent=false")
                        .to("spring-ai-chat:small?chatModel=#chatModel&maxFileSize=40");

                // Route 4: No limit (maxFileSize=0)
                // Uses large-file.bin (150 bytes) - should succeed because limit is disabled
                from("direct:file-size-no-limit")
                        .pollEnrich("file:" + testFilesPath + "?fileName=large-file.bin&noop=true&idempotent=false")
                        .to("spring-ai-chat:nolimit?chatModel=#chatModel&maxFileSize=0");

                // Route 5: WrappedFile with size limit
                // Uses large-file.bin (150 bytes) - should fail with 100 byte limit
                from("direct:wrapped-file-size-limit")
                        .pollEnrich("file:" + testFilesPath + "?fileName=large-file.bin&noop=true&idempotent=false")
                        .setHeader(SpringAiChatConstants.USER_MESSAGE, constant("What is in this file?"))
                        .to("spring-ai-chat:wrapped?chatModel=#chatModel&maxFileSize=100");
            }
        };
    }
}
