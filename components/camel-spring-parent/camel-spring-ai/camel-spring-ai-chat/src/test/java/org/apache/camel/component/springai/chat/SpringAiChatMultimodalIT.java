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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for multimodal capabilities (image and audio input).
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiChatMultimodalIT extends OllamaTestSupport {

    @Test
    public void testImageInputWithByteArray() throws IOException {
        // Create a simple test image (red square)
        byte[] imageData = createTestImage(Color.RED, 100, 100);

        var exchange = template().request("direct:multimodal", e -> {
            e.getIn().setBody(imageData);
            e.getIn().setHeader(SpringAiChatConstants.USER_MESSAGE, "What color is this image?");
            e.getIn().setHeader(SpringAiChatConstants.MEDIA_TYPE, "image/png");
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
        // Note: Response quality depends on model's multimodal capabilities
    }

    @Test
    public void testImageInputWithConfiguredUserMessage() throws IOException {
        byte[] imageData = createTestImage(Color.BLUE, 150, 150);

        var exchange = template().request("direct:multimodal-configured", e -> {
            e.getIn().setBody(imageData);
            e.getIn().setHeader(SpringAiChatConstants.MEDIA_TYPE, "image/png");
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
    }

    @Test
    public void testImageInputWithSystemMessage() throws IOException {
        byte[] imageData = createTestImage(Color.GREEN, 200, 200);

        var exchange = template().request("direct:multimodal-system", e -> {
            e.getIn().setBody(imageData);
            e.getIn().setHeader(SpringAiChatConstants.USER_MESSAGE, "Describe this image.");
            e.getIn().setHeader(SpringAiChatConstants.MEDIA_TYPE, "image/png");
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
    }

    @Test
    public void testMultimodalWithUserMessageObject() throws IOException {
        // Create a UserMessage directly with media
        byte[] imageData = createTestImage(Color.YELLOW, 100, 100);
        Media media = Media.builder()
                .mimeType(MimeTypeUtils.IMAGE_PNG)
                .data(imageData)
                .build();

        UserMessage userMessage = UserMessage.builder()
                .text("What is in this image?")
                .media(media)
                .build();

        String response = template().requestBody("direct:multimodal", userMessage, String.class);
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
    }

    @Test
    public void testMultimodalDefaultImageType() throws IOException {
        // Test without specifying media type (should default to image/png)
        byte[] imageData = createTestImage(Color.MAGENTA, 120, 120);

        var exchange = template().request("direct:multimodal", e -> {
            e.getIn().setBody(imageData);
            e.getIn().setHeader(SpringAiChatConstants.USER_MESSAGE, "Analyze this image.");
            // No MEDIA_TYPE header - should default to image/png
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
    }

    @Test
    public void testMultimodalWithTokenTracking() throws IOException {
        byte[] imageData = createTestImage(Color.CYAN, 100, 100);

        var exchange = template().request("direct:multimodal", e -> {
            e.getIn().setBody(imageData);
            e.getIn().setHeader(SpringAiChatConstants.USER_MESSAGE, "Describe this.");
            e.getIn().setHeader(SpringAiChatConstants.MEDIA_TYPE, "image/png");
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();

        // Verify token usage is tracked for multimodal requests
        Integer totalTokens = exchange.getMessage().getHeader(SpringAiChatConstants.TOTAL_TOKEN_COUNT, Integer.class);
        // Note: Some models may not report token counts for multimodal requests
        if (totalTokens != null) {
            assertThat(totalTokens).isGreaterThan(0);
        }
    }

    @Test
    public void testMultimodalWithJpegImage() throws IOException {
        byte[] imageData = createTestImageJpeg(Color.ORANGE, 150, 150);

        var exchange = template().request("direct:multimodal", e -> {
            e.getIn().setBody(imageData);
            e.getIn().setHeader(SpringAiChatConstants.USER_MESSAGE, "What do you see?");
            e.getIn().setHeader(SpringAiChatConstants.MEDIA_TYPE, "image/jpeg");
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
    }

    /**
     * Helper method to create a test image as PNG
     */
    private byte[] createTestImage(Color color, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    /**
     * Helper method to create a test image as JPEG
     */
    private byte[] createTestImageJpeg(Color color, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                bindChatModel(this.getCamelContext());

                from("direct:multimodal")
                        .to("spring-ai-chat:multimodal?chatModel=#chatModel");

                from("direct:multimodal-configured")
                        .to("spring-ai-chat:multimodal-config?chatModel=#chatModel&userMessage=Describe what you see in this image.");

                from("direct:multimodal-system")
                        .to("spring-ai-chat:multimodal-sys?chatModel=#chatModel&systemMessage=You are an expert image analyst. Provide detailed descriptions.");
            }
        };
    }
}
