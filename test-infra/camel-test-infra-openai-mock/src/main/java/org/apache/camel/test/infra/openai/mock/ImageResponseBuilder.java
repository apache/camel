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
package org.apache.camel.test.infra.openai.mock;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Builds the JSON payload returned by the image generation and image edit endpoints.
 */
public class ImageResponseBuilder {

    private static final long FIXED_CREATED = 1750000000L;

    private final ObjectMapper objectMapper;

    public ImageResponseBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String createImageResponse(ImageExpectation expectation) throws Exception {
        List<String> base64Images = expectation.getBase64Images();
        List<String> imageUrls = expectation.getImageUrls();

        if (base64Images.isEmpty() && imageUrls.isEmpty()) {
            throw new IllegalStateException(
                    "No image data configured for the image expectation. "
                                            + "Call replyWithImage() or replyWithImageUrl()");
        }

        ObjectNode response = objectMapper.createObjectNode();
        response.put("created", FIXED_CREATED);

        ArrayNode data = response.putArray("data");
        List<String> revisedPrompts = expectation.getRevisedPrompts();

        for (int i = 0; i < base64Images.size(); i++) {
            ObjectNode entry = data.addObject();
            entry.put("b64_json", base64Images.get(i));
            addRevisedPrompt(entry, revisedPrompts, i);
        }
        for (int i = 0; i < imageUrls.size(); i++) {
            ObjectNode entry = data.addObject();
            entry.put("url", imageUrls.get(i));
            addRevisedPrompt(entry, revisedPrompts, base64Images.size() + i);
        }

        if (expectation.getOutputFormat() != null) {
            response.put("output_format", expectation.getOutputFormat());
        }
        if (expectation.getSize() != null) {
            response.put("size", expectation.getSize());
        }
        if (expectation.getInputTokens() != null && expectation.getOutputTokens() != null) {
            int inputTokens = expectation.getInputTokens();
            int outputTokens = expectation.getOutputTokens();
            ObjectNode usage = response.putObject("usage");
            usage.put("input_tokens", inputTokens);
            usage.put("output_tokens", outputTokens);
            usage.put("total_tokens", inputTokens + outputTokens);
            ObjectNode details = usage.putObject("input_tokens_details");
            details.put("image_tokens", 0);
            details.put("text_tokens", inputTokens);
        }

        return objectMapper.writeValueAsString(response);
    }

    private void addRevisedPrompt(ObjectNode entry, List<String> revisedPrompts, int index) {
        if (index < revisedPrompts.size() && revisedPrompts.get(index) != null) {
            entry.put("revised_prompt", revisedPrompts.get(index));
        }
    }
}
