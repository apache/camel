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
package org.apache.camel.component.huggingface.tasks;

import java.nio.charset.StandardCharsets;

import ai.djl.modality.Input;
import ai.djl.modality.Output;
import org.apache.camel.Exchange;
import org.apache.camel.component.huggingface.HuggingFaceEndpoint;

/**
 * Predictor for the TEXT_TO_IMAGE task, generating images from text prompts.
 *
 * <p>
 * This predictor uses Hugging Face's text-to-image pipeline (e.g., Stable Diffusion) to create images from
 * descriptions.
 * </p>
 *
 * <p>
 * <b>Input Contract (Camel Message Body):</b>
 * </p>
 * <ul>
 * <li>{@code String}: The text prompt describing the image (e.g., "A cute cat").</li>
 * </ul>
 *
 * <p>
 * <b>Output Contract (Camel Message Body):</b>
 * </p>
 * <ul>
 * <li>{@code byte[]}: Raw PNG image bytes.</li>
 * </ul>
 *
 * <p>
 * <b>Camel Headers Used:</b>
 * </p>
 * <ul>
 * <li>{@code Content-Type}: Set to "image/png".</li>
 * <li>{@code HuggingFaceConstants.OUTPUT}: The same image byte[] (for convenience).</li>
 * </ul>
 *
 * <p>
 * <b>Relevant HuggingFaceConfiguration Properties:</b>
 * </p>
 * <ul>
 * <li>{@code modelId}: Required String, e.g., "CompVis/stable-diffusion-v1-4".</li>
 * <li>{@code revision}: Optional String, model revision (default "main").</li>
 * <li>{@code device}: Optional String, inference device (default "auto").</li>
 * </ul>
 *
 * <p>
 * <b>Python Model Input/Output Expectations:</b>
 * </p>
 * <ul>
 * <li><b>Input</b>: String text prompt. Models must support text-to-image pipeline (e.g., Stable Diffusion
 * variants).</li>
 * <li><b>Output</b>: Raw PNG bytes or JSON error dict. Compatible models return this format via HF diffusers
 * library.</li>
 * </ul>
 * <p>
 * To ensure model interchangeability, use diffusion-tuned models. Note: Generation can be slow on CPU.
 * </p>
 */
public class TextToImagePredictor extends AbstractTaskPredictor {
    public TextToImagePredictor(HuggingFaceEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected String getRequirements() {
        return """
                torch>=2.0.0
                accelerate
                diffusers>=0.20.0
                """;
    }

    @Override
    protected String getPythonScript() {
        return loadPythonScript("text_to_image.py", config.getModelId(), config.getDevice());
    }

    @Override
    protected Input prepareInput(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        if (!(body instanceof String)) {
            throw new IllegalArgumentException("Body must be String for TEXT_TO_IMAGE");
        }
        Input input = new Input();
        input.add("data", ((String) body).getBytes(StandardCharsets.UTF_8));
        return input;
    }

    @Override
    protected void processOutput(Exchange exchange, Output output) throws Exception {
        byte[] imageBytes = output.getAsBytes("data");
        exchange.getMessage().setBody(imageBytes);
        exchange.getMessage().setHeader("Content-Type", "image/png");
    }
}
