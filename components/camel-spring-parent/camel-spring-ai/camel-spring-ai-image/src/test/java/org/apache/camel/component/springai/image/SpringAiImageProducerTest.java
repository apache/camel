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
package org.apache.camel.component.springai.image;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the options the producer sends to the ImageModel, with a fake ImageModel.
 */
public class SpringAiImageProducerTest extends CamelTestSupport {

    private static final String ENDPOINT = "spring-ai-image:gen?imageModel=#imageModel&n=2&width=512&height=256"
                                           + "&model=endpoint-model&responseFormat=b64_json&style=vivid";

    private final List<ImagePrompt> prompts = new ArrayList<>();

    @BindToRegistry("imageModel")
    private final ImageModel imageModel = prompt -> {
        prompts.add(prompt);
        return new ImageResponse(List.of(new ImageGeneration(new Image(null, "aGVsbG8="))));
    };

    @Test
    public void textBodyUsesEndpointOptionsAndHeaders() {
        template.sendBodyAndHeader(ENDPOINT, "A camel", SpringAiImageHeaders.WIDTH, 1024);

        assertThat(prompts).hasSize(1);
        assertThat(prompts.get(0).getInstructions().get(0).getText()).isEqualTo("A camel");
        assertOptions(prompts.get(0).getOptions(), 2, 1024, 256, "endpoint-model", "b64_json", "vivid");
    }

    @Test
    public void imagePromptWithoutOptionsUsesEndpointOptions() {
        template.sendBody(ENDPOINT, new ImagePrompt("A camel"));

        assertThat(prompts).hasSize(1);
        assertThat(prompts.get(0).getInstructions().get(0).getText()).isEqualTo("A camel");
        assertOptions(prompts.get(0).getOptions(), 2, 512, 256, "endpoint-model", "b64_json", "vivid");
    }

    @Test
    public void imagePromptWithNullOptionsUsesEndpointOptions() {
        template.sendBody(ENDPOINT, new ImagePrompt(List.of(new ImageMessage("A camel", 0.5f))));

        assertThat(prompts).hasSize(1);
        ImageMessage message = prompts.get(0).getInstructions().get(0);
        assertThat(message.getText()).isEqualTo("A camel");
        assertThat(message.getWeight()).isEqualTo(0.5f);
        assertOptions(prompts.get(0).getOptions(), 2, 512, 256, "endpoint-model", "b64_json", "vivid");
    }

    @Test
    public void imagePromptOptionsWinOverHeadersAndEndpointOptions() {
        ImageOptions own = ImageOptionsBuilder.builder().width(2048).model("prompt-model").build();

        template.sendBodyAndHeader(ENDPOINT, new ImagePrompt("A camel", own), SpringAiImageHeaders.HEIGHT, 128);

        assertThat(prompts).hasSize(1);
        assertOptions(prompts.get(0).getOptions(), 2, 2048, 128, "prompt-model", "b64_json", "vivid");
    }

    @Test
    public void imagePromptWithProviderOptionsIsUsedAsIs() {
        ImagePrompt prompt = new ImagePrompt("A camel", new ProviderImageOptions());

        template.sendBody(ENDPOINT, prompt);

        assertThat(prompts).hasSize(1);
        assertThat(prompts.get(0)).isSameAs(prompt);
    }

    @Test
    public void genericOptionsTypeIsTheTypeOfTheBuilderAndOfAPromptWithoutOptions() {
        // the producer tells the generic options from those of a provider by their class, as Spring AI has no
        // public type for them: if this fails, Spring AI changed the type and the producer must detect it otherwise
        assertThat(ImageOptionsBuilder.builder().n(1).width(1024).model("m").style("s").build())
                .as("options of ImageOptionsBuilder").isExactlyInstanceOf(SpringAiImageProducer.GENERIC_OPTIONS_TYPE);
        assertThat(new ImagePrompt("A camel").getOptions())
                .as("options of new ImagePrompt(text)").isExactlyInstanceOf(SpringAiImageProducer.GENERIC_OPTIONS_TYPE);
        assertThat(new ProviderImageOptions()).isNotInstanceOf(SpringAiImageProducer.GENERIC_OPTIONS_TYPE);
    }

    private static void assertOptions(
            ImageOptions options, Integer n, Integer width, Integer height, String model, String responseFormat,
            String style) {
        assertThat(options).isNotNull();
        assertThat(options.getN()).isEqualTo(n);
        assertThat(options.getWidth()).isEqualTo(width);
        assertThat(options.getHeight()).isEqualTo(height);
        assertThat(options.getModel()).isEqualTo(model);
        assertThat(options.getResponseFormat()).isEqualTo(responseFormat);
        assertThat(options.getStyle()).isEqualTo(style);
    }

    /**
     * Stands for the options of a provider, such as OpenAiImageOptions, which have settings of their own.
     */
    private static final class ProviderImageOptions implements ImageOptions {

        @Override
        public Integer getN() {
            return null;
        }

        @Override
        public String getModel() {
            return "provider-model";
        }

        @Override
        public Integer getWidth() {
            return null;
        }

        @Override
        public Integer getHeight() {
            return null;
        }

        @Override
        public String getResponseFormat() {
            return null;
        }

        @Override
        public String getStyle() {
            return null;
        }
    }
}
