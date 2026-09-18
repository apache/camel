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

import java.io.InputStream;
import java.util.List;

import com.openai.core.MultipartField;
import com.openai.models.images.ImageEditParams;
import com.openai.models.images.ImagesResponse;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * OpenAI producer for image editing. The message body carries the image (or a {@link List} of images for the models
 * that accept several reference images) and the prompt describes the edit. The produced body is the edited image as a
 * {@code byte[]}, or its URL as a {@code String} when the model is asked for the {@code url} response format.
 */
public class OpenAIImageEditProducer extends DefaultProducer {

    public OpenAIImageEditProducer(OpenAIEndpoint endpoint) {
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

        String model = OpenAIImageSupport.resolveModel(in, config, "image-edit");
        String prompt = OpenAIImageSupport.resolveParameter(in, OpenAIConstants.IMAGE_PROMPT, config.getImagePrompt(),
                String.class);
        if (ObjectHelper.isEmpty(prompt)) {
            // the body carries the image, so unlike image-generation there is no body to fall back on
            throw new IllegalArgumentException(
                    "The edit prompt must be specified via the imagePrompt parameter or the "
                                               + OpenAIConstants.IMAGE_PROMPT
                                               + " header, because the message body carries the image to edit");
        }

        String outputFormat = OpenAIImageSupport.resolveParameter(in, OpenAIConstants.IMAGE_OUTPUT_FORMAT,
                config.getImageOutputFormat(), String.class);

        List<InputStream> images = OpenAIImageSupport.resolveImages(in);
        InputStream mask = null;

        try {
            mask = OpenAIImageSupport.resolveMask(in);

            ImageEditParams.Builder params = ImageEditParams.builder()
                    .model(model)
                    .prompt(prompt)
                    .image(imageField(in, images));

            if (mask != null) {
                // the API documents the mask as a PNG, so the part is always declared as one
                params.mask(MultipartField.<InputStream> builder()
                        .value(mask)
                        .filename("mask.png")
                        .contentType("image/png")
                        .build());
            }

            String size = OpenAIImageSupport.resolveParameter(in, OpenAIConstants.IMAGE_SIZE, config.getImageSize(),
                    String.class);
            if (ObjectHelper.isNotEmpty(size)) {
                params.size(size);
            }

            String quality = OpenAIImageSupport.resolveParameter(in, OpenAIConstants.IMAGE_QUALITY,
                    config.getImageQuality(), String.class);
            if (ObjectHelper.isNotEmpty(quality)) {
                params.quality(ImageEditParams.Quality.of(quality));
            }

            // only dall-e-2 accepts response_format on this endpoint, so it is only sent when the route asked for it
            String responseFormat = OpenAIImageSupport.resolveParameter(in, OpenAIConstants.IMAGE_RESPONSE_FORMAT,
                    config.getImageResponseFormat(), String.class);
            if (ObjectHelper.isNotEmpty(responseFormat)) {
                params.responseFormat(ImageEditParams.ResponseFormat.of(responseFormat));
            }

            Integer count = OpenAIImageSupport.resolveParameter(in, OpenAIConstants.IMAGE_COUNT, config.getImageCount(),
                    Integer.class);
            if (count != null) {
                params.n(count.longValue());
            }

            String background = OpenAIImageSupport.resolveParameter(in, OpenAIConstants.IMAGE_BACKGROUND,
                    config.getImageBackground(), String.class);
            if (ObjectHelper.isNotEmpty(background)) {
                params.background(ImageEditParams.Background.of(background));
            }

            if (ObjectHelper.isNotEmpty(outputFormat)) {
                params.outputFormat(ImageEditParams.OutputFormat.of(outputFormat));
            }

            Integer outputCompression = OpenAIImageSupport.resolveParameter(in,
                    OpenAIConstants.IMAGE_OUTPUT_COMPRESSION, config.getImageOutputCompression(), Integer.class);
            if (outputCompression != null) {
                params.outputCompression(outputCompression.longValue());
            }

            String inputFidelity = OpenAIImageSupport.resolveParameter(in, OpenAIConstants.IMAGE_INPUT_FIDELITY,
                    config.getImageInputFidelity(), String.class);
            if (ObjectHelper.isNotEmpty(inputFidelity)) {
                params.inputFidelity(ImageEditParams.InputFidelity.of(inputFidelity));
            }

            ImagesResponse response = getEndpoint().getClient().images().edit(params.build());

            OpenAIImageSupport.applyResponse(exchange, response, outputFormat, config.isStoreFullResponse());
        } finally {
            images.forEach(OpenAIImageSupport::closeQuietly);
            OpenAIImageSupport.closeQuietly(mask);
        }
    }

    private static MultipartField<ImageEditParams.Image> imageField(Message in, List<InputStream> images) {
        ImageEditParams.Image value = images.size() == 1
                ? ImageEditParams.Image.ofInputStream(images.get(0))
                : ImageEditParams.Image.ofInputStreams(images);

        // the API rejects the upload on the content type of the part, which the SDK leaves as text/plain unless
        // it is declared here; a raw byte[] body carries neither that nor a file name
        String mimeType = OpenAIImageSupport.resolveImageMimeType(in);
        return MultipartField.<ImageEditParams.Image> builder()
                .value(value)
                .filename(OpenAIImageSupport.filenameFor(in, "image", mimeType))
                .contentType(mimeType)
                .build();
    }
}
