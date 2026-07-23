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
package org.apache.camel.component.openai;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests vision model input with the different body types produced by file-based and cloud storage components:
 * WrappedFile/GenericFile, byte[] and InputStream (CAMEL-23739).
 */
public class OpenAIVisionBodyTypesMockTest extends CamelTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, (byte) 0xFF, 0x00, 0x01, 0x02 };
    private static final byte[] JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, (byte) 0xCA, (byte) 0xFE, 0x00, 0x42 };

    private final Path imagesDir = createTempDir();

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("describe-png")
            .assertRequest(request -> assertImageDataUrl(request, "image/png", PNG_BYTES))
            .replyWith("png response")
            .end()
            .when("describe-jpeg")
            .assertRequest(request -> assertImageDataUrl(request, "image/jpeg", JPEG_BYTES))
            .replyWith("jpeg response")
            .end()
            .when("describe-webp")
            .assertRequest(request -> assertImageDataUrl(request, "image/webp", PNG_BYTES))
            .replyWith("webp response")
            .end()
            .when("describe-avif")
            .assertRequest(request -> assertImageDataUrl(request, "image/avif", PNG_BYTES))
            .replyWith("avif response")
            .end()
            .when("hello bytes")
            .replyWith("text response")
            .end()
            .when("prompt from file")
            .replyWith("file text response")
            .end()
            .when("<note>xml prompt</note>")
            .replyWith("xml text response")
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:chat")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&baseUrl=" + openAIMock.getBaseUrl()
                            + "/v1");

                // consumes GenericFile bodies, the typical vision use case from the file component
                from("file:" + imagesDir + "?noop=true&initialDelay=0")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&userMessage=describe-png&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1")
                        .to("mock:fileResult");
            }
        };
    }

    @Test
    void genericFileBodyFromFileConsumerIsSentAsImage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:fileResult");
        mock.expectedBodiesReceived("png response");

        Files.write(imagesDir.resolve("picture.png"), PNG_BYTES);

        mock.assertIsSatisfied();
    }

    @Test
    void byteArrayBodyWithCloudContentTypeHeaderIsSentAsImage() {
        Exchange result = template.request("direct:chat", e -> {
            e.getIn().setBody(PNG_BYTES);
            e.getIn().setHeader("CamelAwsS3ContentType", "image/png");
            e.getIn().setHeader(OpenAIConstants.USER_MESSAGE, "describe-png");
        });
        assertEquals("png response", result.getMessage().getBody(String.class));
    }

    @Test
    void inputStreamBodyWithContentTypeHeaderIsSentAsImage() {
        Exchange result = template.request("direct:chat", e -> {
            e.getIn().setBody(new ByteArrayInputStream(JPEG_BYTES));
            e.getIn().setHeader(Exchange.CONTENT_TYPE, "image/jpeg");
            e.getIn().setHeader(OpenAIConstants.USER_MESSAGE, "describe-jpeg");
        });
        assertEquals("jpeg response", result.getMessage().getBody(String.class));
    }

    @Test
    void mediaTypeHeaderOverridesOtherContentTypeHeaders() {
        Exchange result = template.request("direct:chat", e -> {
            e.getIn().setBody(PNG_BYTES);
            e.getIn().setHeader(Exchange.CONTENT_TYPE, "application/octet-stream");
            e.getIn().setHeader(OpenAIConstants.MEDIA_TYPE, "image/webp");
            e.getIn().setHeader(OpenAIConstants.USER_MESSAGE, "describe-webp");
        });
        assertEquals("webp response", result.getMessage().getBody(String.class));
    }

    @Test
    void byteArrayBodyWithFileNameHeaderUsesExtensionDetection() {
        Exchange result = template.request("direct:chat", e -> {
            e.getIn().setBody(PNG_BYTES);
            e.getIn().setHeader(Exchange.FILE_NAME, "photo.png");
            e.getIn().setHeader(OpenAIConstants.USER_MESSAGE, "describe-png");
        });
        assertEquals("png response", result.getMessage().getBody(String.class));
    }

    @Test
    void byteArrayBodyWithoutMimeInfoIsTreatedAsText() {
        Exchange result = template.request("direct:chat",
                e -> e.getIn().setBody("hello bytes".getBytes(StandardCharsets.UTF_8)));
        assertEquals("text response", result.getMessage().getBody(String.class));
    }

    @Test
    void textFileBodyContentIsUsedAsPrompt() throws Exception {
        Path textFile = Files.createTempFile("camel-openai-prompt", ".txt");
        Files.writeString(textFile, "prompt from file");

        Exchange result = template.request("direct:chat", e -> e.getIn().setBody(textFile.toFile()));
        assertEquals("file text response", result.getMessage().getBody(String.class));
    }

    @Test
    void avifFileNameExtensionIsDetectedFromMimeTypeTable() {
        Exchange result = template.request("direct:chat", e -> {
            e.getIn().setBody(PNG_BYTES);
            e.getIn().setHeader(Exchange.FILE_NAME, "photo.avif");
            e.getIn().setHeader(OpenAIConstants.USER_MESSAGE, "describe-avif");
        });
        assertEquals("avif response", result.getMessage().getBody(String.class));
    }

    @Test
    void xmlFileBodyContentIsUsedAsPrompt() throws Exception {
        Path xmlFile = Files.createTempFile("camel-openai-prompt", ".xml");
        Files.writeString(xmlFile, "<note>xml prompt</note>");

        Exchange result = template.request("direct:chat", e -> e.getIn().setBody(xmlFile.toFile()));
        assertEquals("xml text response", result.getMessage().getBody(String.class));
    }

    @Test
    void imageBodyWithoutUserMessageFails() {
        Exchange result = template.request("direct:chat", e -> {
            e.getIn().setBody(PNG_BYTES);
            e.getIn().setHeader(Exchange.CONTENT_TYPE, "image/png");
        });
        Exception exception = result.getException();
        assertInstanceOf(IllegalArgumentException.class, exception);
        assertTrue(exception.getMessage().contains("User message"));
    }

    @Test
    void unsupportedFileTypeFails() throws Exception {
        Path binFile = Files.createTempFile("camel-openai-unsupported", ".bin");
        Files.write(binFile, PNG_BYTES);

        Exchange result = template.request("direct:chat", e -> {
            e.getIn().setBody(binFile.toFile());
            e.getIn().setHeader(OpenAIConstants.USER_MESSAGE, "describe-png");
        });
        Exception exception = result.getException();
        assertInstanceOf(IllegalArgumentException.class, exception);
        assertTrue(exception.getMessage().contains("Only text and image files are supported"));
    }

    private static void assertImageDataUrl(String request, String expectedMime, byte[] expectedBytes) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(request);
            JsonNode messages = root.path("messages");
            JsonNode lastMessage = messages.get(messages.size() - 1);
            String url = null;
            for (JsonNode part : lastMessage.path("content")) {
                if ("image_url".equals(part.path("type").asText())) {
                    url = part.path("image_url").path("url").asText();
                }
            }
            assertNotNull(url, "Expected an image_url content part in the request");
            String prefix = "data:" + expectedMime + ";base64,";
            assertTrue(url.startsWith(prefix), "Expected data URL with MIME type " + expectedMime);
            assertArrayEquals(expectedBytes, Base64.getDecoder().decode(url.substring(prefix.length())));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("camel-openai-vision-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
