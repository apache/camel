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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Expectation for the image generation and image edit endpoints. An expectation without an expected prompt matches any
 * request, which is what the image edit endpoint needs because its request is multipart and is not parsed by the mock.
 */
public class ImageExpectation {

    private String expectedPrompt;
    private final List<String> base64Images = new ArrayList<>();
    private final List<String> imageUrls = new ArrayList<>();
    private final List<String> revisedPrompts = new ArrayList<>();
    private String outputFormat;
    private String size;
    private Consumer<byte[]> requestAssertion;
    private Integer inputTokens;
    private Integer outputTokens;

    public String getExpectedPrompt() {
        return expectedPrompt;
    }

    public void setExpectedPrompt(String expectedPrompt) {
        this.expectedPrompt = expectedPrompt;
    }

    public boolean matches(String prompt) {
        return expectedPrompt == null || expectedPrompt.equals(prompt);
    }

    public void addBase64Image(String base64Image) {
        base64Images.add(base64Image);
    }

    public List<String> getBase64Images() {
        return base64Images;
    }

    public void addImageUrl(String imageUrl) {
        imageUrls.add(imageUrl);
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void addRevisedPrompt(String revisedPrompt) {
        revisedPrompts.add(revisedPrompt);
    }

    public List<String> getRevisedPrompts() {
        return revisedPrompts;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public Consumer<byte[]> getRequestAssertion() {
        return requestAssertion;
    }

    public void setRequestAssertion(Consumer<byte[]> requestAssertion) {
        this.requestAssertion = requestAssertion;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public void setUsage(int inputTokens, int outputTokens) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }
}
