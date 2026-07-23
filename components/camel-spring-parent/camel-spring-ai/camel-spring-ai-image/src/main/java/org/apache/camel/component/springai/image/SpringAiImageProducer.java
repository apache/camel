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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;

public class SpringAiImageProducer extends DefaultProducer {

    public SpringAiImageProducer(SpringAiImageEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public SpringAiImageEndpoint getEndpoint() {
        return (SpringAiImageEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final Message message = exchange.getMessage();
        final ImageModel model = getEndpoint().getConfiguration().getImageModel();
        final Object body = message.getBody();

        ImagePrompt imagePrompt;

        if (body instanceof ImagePrompt prompt) {
            imagePrompt = prompt;
        } else {
            String text = message.getBody(String.class);
            if (text != null) {
                ImageOptions options = buildImageOptions(exchange);
                imagePrompt = new ImagePrompt(text, options);
            } else {
                throw new IllegalArgumentException(
                        "Message body must be a String or ImagePrompt, but was: "
                                                   + (body != null ? body.getClass().getName() : "null"));
            }
        }

        ImageResponse response = model.call(imagePrompt);

        if (response.getResults() != null && !response.getResults().isEmpty()) {
            if (response.getResults().size() == 1) {
                ImageGeneration generation = response.getResults().get(0);
                message.setBody(generation.getOutput());
                message.setHeader(SpringAiImageHeaders.IMAGE_GENERATION, generation);
            } else {
                List<Image> images = response.getResults().stream()
                        .map(ImageGeneration::getOutput)
                        .toList();
                message.setBody(images);
                message.setHeader(SpringAiImageHeaders.IMAGE_GENERATIONS, response.getResults());
            }
        }

        if (response.getMetadata() != null) {
            message.setHeader(SpringAiImageHeaders.RESPONSE_METADATA, response.getMetadata());
        }
    }

    private ImageOptions buildImageOptions(Exchange exchange) {
        SpringAiImageConfiguration config = getEndpoint().getConfiguration();
        ImageOptionsBuilder builder = ImageOptionsBuilder.builder();

        // Headers override configuration
        Integer n = getOption(exchange, SpringAiImageHeaders.N, Integer.class, config.getN());
        if (n != null) {
            builder.N(n);
        }

        Integer width = getOption(exchange, SpringAiImageHeaders.WIDTH, Integer.class, config.getWidth());
        if (width != null) {
            builder.width(width);
        }

        Integer height = getOption(exchange, SpringAiImageHeaders.HEIGHT, Integer.class, config.getHeight());
        if (height != null) {
            builder.height(height);
        }

        String model = getOption(exchange, SpringAiImageHeaders.MODEL, String.class, config.getModel());
        if (model != null) {
            builder.model(model);
        }

        String responseFormat = getOption(exchange, SpringAiImageHeaders.RESPONSE_FORMAT, String.class,
                config.getResponseFormat());
        if (responseFormat != null) {
            builder.responseFormat(responseFormat);
        }

        String style = getOption(exchange, SpringAiImageHeaders.STYLE, String.class, config.getStyle());
        if (style != null) {
            builder.style(style);
        }

        return builder.build();
    }

    private <T> T getOption(Exchange exchange, String headerName, Class<T> type, T configValue) {
        T headerValue = exchange.getIn().getHeader(headerName, type);
        return headerValue != null ? headerValue : configValue;
    }
}
