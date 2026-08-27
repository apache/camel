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

import com.openai.models.images.ImageGenerateParams;
import com.openai.models.images.ImagesResponse;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * OpenAI producer for image generation. The message body is the prompt and the produced body is the generated image as
 * a {@code byte[]}, or its URL as a {@code String} when the model is asked for the {@code url} response format.
 */
public class OpenAIImageGenerationProducer extends DefaultProducer {

    public OpenAIImageGenerationProducer(OpenAIEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public OpenAIEndpoint getEndpoint() {
        return (OpenAIEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        OpenAIConfiguration config = getEndpoint().getConfiguration();
        Message in = exchange.getIn();

        String model = OpenAIImageSupport.resolveModel(in, config, "image-generation");
        String prompt = resolvePrompt(in, config);

        ImageGenerateParams.Builder params = ImageGenerateParams.builder()
                .model(model)
                .prompt(prompt);

        String size = OpenAIImageSupport.resolveParameter(in, OpenAIConstants.IMAGE_SIZE, config.getImageSize(),
                String.class);
        if (ObjectHelper.isNotEmpty(size)) {
            params.size(size);
        }

        String quality = OpenAIImageSupport.resolveParameter(in, OpenAIConstants.IMAGE_QUALITY, config.getImageQuality(),
                String.class);
        if (ObjectHelper.isNotEmpty(quality)) {
            params.quality(ImageGenerateParams.Quality.of(quality));
        }

        // the GPT image models always return base64 and reject response_format outright,
        // so it is only sent when the route asked for it
        String responseFormat = OpenAIImageSupport.resolveParameter(in, OpenAIConstants.IMAGE_RESPONSE_FORMAT,
                config.getImageResponseFormat(), String.class);
        if (ObjectHelper.isNotEmpty(responseFormat)) {
            params.responseFormat(ImageGenerateParams.ResponseFormat.of(responseFormat));
        }

        Integer count = OpenAIImageSupport.resolveParameter(in, OpenAIConstants.IMAGE_COUNT, config.getImageCount(),
                Integer.class);
        if (count != null) {
            params.n(count.longValue());
        }

        String background = OpenAIImageSupport.resolveParameter(in, OpenAIConstants.IMAGE_BACKGROUND,
                config.getImageBackground(), String.class);
        if (ObjectHelper.isNotEmpty(background)) {
            params.background(ImageGenerateParams.Background.of(background));
        }

        String outputFormat = OpenAIImageSupport.resolveParameter(in, OpenAIConstants.IMAGE_OUTPUT_FORMAT,
                config.getImageOutputFormat(), String.class);
        if (ObjectHelper.isNotEmpty(outputFormat)) {
            params.outputFormat(ImageGenerateParams.OutputFormat.of(outputFormat));
        }

        Integer outputCompression = OpenAIImageSupport.resolveParameter(in, OpenAIConstants.IMAGE_OUTPUT_COMPRESSION,
                config.getImageOutputCompression(), Integer.class);
        if (outputCompression != null) {
            params.outputCompression(outputCompression.longValue());
        }

        String style = OpenAIImageSupport.resolveParameter(in, OpenAIConstants.IMAGE_STYLE, config.getImageStyle(),
                String.class);
        if (ObjectHelper.isNotEmpty(style)) {
            params.style(ImageGenerateParams.Style.of(style));
        }

        String moderation = OpenAIImageSupport.resolveParameter(in, OpenAIConstants.IMAGE_MODERATION,
                config.getImageModeration(), String.class);
        if (ObjectHelper.isNotEmpty(moderation)) {
            params.moderation(ImageGenerateParams.Moderation.of(moderation));
        }

        ImagesResponse response = getEndpoint().getClient().images().generate(params.build());

        OpenAIImageSupport.applyResponse(exchange, response, outputFormat, config.isStoreFullResponse());
    }

    private static String resolvePrompt(Message in, OpenAIConfiguration config) {
        String prompt = OpenAIImageSupport.resolveParameter(in, OpenAIConstants.IMAGE_PROMPT, config.getImagePrompt(),
                String.class);
        if (ObjectHelper.isEmpty(prompt)) {
            prompt = in.getBody(String.class);
        }
        if (ObjectHelper.isEmpty(prompt)) {
            throw new IllegalArgumentException(
                    "The message body must contain the image prompt, or it must be set via the imagePrompt parameter "
                                               + "or the " + OpenAIConstants.IMAGE_PROMPT + " header");
        }
        return prompt;
    }
}
