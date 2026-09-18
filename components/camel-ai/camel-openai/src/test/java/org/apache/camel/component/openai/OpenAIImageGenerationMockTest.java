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

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.openai.models.images.ImagesResponse;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenAIImageGenerationMockTest extends CamelTestSupport {

    private static final byte[] FIRST_IMAGE = "FAKE-PNG-IMAGE-ONE".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SECOND_IMAGE = "FAKE-PNG-IMAGE-TWO".getBytes(StandardCharsets.UTF_8);

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .whenImageGeneration("A red bicycle")
            .replyWithImage(FIRST_IMAGE)
            .withImageUsage(11, 22)
            .end()
            .whenImageGeneration("Two cats")
            .replyWithImage(FIRST_IMAGE)
            .replyWithImage(SECOND_IMAGE)
            .end()
            .whenImageGeneration("A hosted logo")
            .replyWithImageUrl("https://example.org/generated.png")
            .end()
            .whenImageGeneration("A cat in a hat")
            .replyWithImage(FIRST_IMAGE)
            .withRevisedPrompt("A photorealistic cat wearing a top hat")
            .end()
            .whenImageGeneration("A webp banner")
            .replyWithImage(FIRST_IMAGE)
            .withImageOutputFormat("webp")
            .end()
            .whenImageGeneration("A jpeg banner")
            .replyWithImage(FIRST_IMAGE)
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:generate")
                        .to("openai:image-generation?imageModel=gpt-image-1&apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("direct:generate-no-model")
                        .to("openai:image-generation?apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("direct:generate-url")
                        .to("openai:image-generation?imageModel=dall-e-3&imageResponseFormat=url&apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("direct:generate-jpeg")
                        .to("openai:image-generation?imageModel=gpt-image-1&imageOutputFormat=jpeg&apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("direct:generate-with-prompt-option")
                        .to("openai:image-generation?imageModel=gpt-image-1&imagePrompt=A red bicycle&apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("direct:generate-store-response")
                        .to("openai:image-generation?imageModel=gpt-image-1&storeFullResponse=true&apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    @Test
    void testSingleImageBecomesByteArrayBody() {
        Exchange result = template.request("direct:generate", e -> e.getIn().setBody("A red bicycle"));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody()).isInstanceOf(byte[].class);
        assertThat(result.getMessage().getBody(byte[].class)).isEqualTo(FIRST_IMAGE);
        assertThat(result.getMessage().getHeader(OpenAIConstants.IMAGE_RESULT_COUNT)).isEqualTo(1);
        assertThat(result.getMessage().getHeader(Exchange.CONTENT_TYPE)).isEqualTo("image/png");
    }

    @Test
    void testUsageHeaders() {
        Exchange result = template.request("direct:generate", e -> e.getIn().setBody("A red bicycle"));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getHeader(OpenAIConstants.IMAGE_INPUT_TOKENS)).isEqualTo(11L);
        assertThat(result.getMessage().getHeader(OpenAIConstants.IMAGE_OUTPUT_TOKENS)).isEqualTo(22L);
        assertThat(result.getMessage().getHeader(OpenAIConstants.IMAGE_TOTAL_TOKENS)).isEqualTo(33L);
    }

    @Test
    void testMultipleImagesBecomeListBody() {
        Exchange result = template.request("direct:generate", e -> {
            e.getIn().setBody("Two cats");
            e.getIn().setHeader(OpenAIConstants.IMAGE_COUNT, 2);
        });

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody()).isInstanceOf(List.class);

        List<?> images = result.getMessage().getBody(List.class);
        assertThat(images).hasSize(2);
        assertThat((byte[]) images.get(0)).isEqualTo(FIRST_IMAGE);
        assertThat((byte[]) images.get(1)).isEqualTo(SECOND_IMAGE);
        assertThat(result.getMessage().getHeader(OpenAIConstants.IMAGE_RESULT_COUNT)).isEqualTo(2);
    }

    @Test
    void testUrlResponseFormatBecomesStringBody() {
        Exchange result = template.request("direct:generate-url", e -> e.getIn().setBody("A hosted logo"));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody()).isEqualTo("https://example.org/generated.png");
        // no bytes were returned, so the exchange must not claim an image content type
        assertThat(result.getMessage().getHeader(Exchange.CONTENT_TYPE)).isNull();
    }

    @Test
    void testRevisedPromptHeaders() {
        Exchange result = template.request("direct:generate", e -> e.getIn().setBody("A cat in a hat"));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getHeader(OpenAIConstants.IMAGE_REVISED_PROMPT))
                .isEqualTo("A photorealistic cat wearing a top hat");
        assertThat(result.getMessage().getHeader(OpenAIConstants.IMAGE_REVISED_PROMPTS, List.class))
                .containsExactly("A photorealistic cat wearing a top hat");
    }

    @Test
    void testContentTypeFromResponseOutputFormat() {
        Exchange result = template.request("direct:generate", e -> e.getIn().setBody("A webp banner"));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getHeader(Exchange.CONTENT_TYPE)).isEqualTo("image/webp");
    }

    @Test
    void testContentTypeFallsBackToRequestedOutputFormat() {
        // the reply carries no output_format, so the format asked for in the request is used instead
        Exchange result = template.request("direct:generate-jpeg", e -> e.getIn().setBody("A jpeg banner"));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getHeader(Exchange.CONTENT_TYPE)).isEqualTo("image/jpeg");
    }

    @Test
    void testPromptFromEndpointOption() {
        Exchange result = template.request("direct:generate-with-prompt-option", e -> e.getIn().setBody(null));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(byte[].class)).isEqualTo(FIRST_IMAGE);
    }

    @Test
    void testPromptHeaderTakesPrecedenceOverBody() {
        Exchange result = template.request("direct:generate", e -> {
            e.getIn().setBody("ignored body");
            e.getIn().setHeader(OpenAIConstants.IMAGE_PROMPT, "A red bicycle");
        });

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(byte[].class)).isEqualTo(FIRST_IMAGE);
    }

    @Test
    void testStoreFullResponse() {
        Exchange result = template.request("direct:generate-store-response", e -> e.getIn().setBody("A red bicycle"));

        assertThat(result.getException()).isNull();
        assertThat(result.getProperty(OpenAIConstants.IMAGE_RESPONSE)).isInstanceOf(ImagesResponse.class);
    }

    @Test
    void testMissingModel() {
        Exchange result = template.request("direct:generate-no-model", e -> e.getIn().setBody("A red bicycle"));

        assertThat(result.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Image model must be specified");
    }

    @Test
    void testMissingPrompt() {
        Exchange result = template.request("direct:generate", e -> e.getIn().setBody(null));

        assertThat(result.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must contain the image prompt");
    }
}
