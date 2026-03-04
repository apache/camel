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
import org.apache.camel.component.huggingface.HuggingFaceConstants;
import org.apache.camel.component.huggingface.HuggingFaceEndpoint;

/**
 * Predictor for the TEXT_GENERATION task, generating text from a prompt.
 *
 * <p>
 * This predictor uses Hugging Face's text-generation pipeline to continue or generate text from input prompts.
 * </p>
 *
 * <p>
 * <b>Input Contract (Camel Message Body):</b>
 * </p>
 * <ul>
 * <li>{@code String}: The text prompt to generate from (e.g., "Once upon a time").</li>
 * </ul>
 *
 * <p>
 * <b>Output Contract (Camel Message Body):</b>
 * </p>
 * <ul>
 * <li>{@code String}: The generated text (e.g., "Once upon a time, there was a...").</li>
 * </ul>
 *
 * <p>
 * <b>Camel Headers Used:</b>
 * </p>
 * <ul>
 * <li>{@code HuggingFaceConstants.OUTPUT}: The same generated text string (for convenience).</li>
 * </ul>
 *
 * <p>
 * <b>Relevant HuggingFaceConfiguration Properties:</b>
 * </p>
 * <ul>
 * <li>{@code modelId}: Required String, e.g., "gpt2".</li>
 * <li>{@code revision}: Optional String, model revision (default "main").</li>
 * <li>{@code device}: Optional String, inference device (default "auto").</li>
 * <li>{@code maxTokens}: Optional int, max new tokens (default 512).</li>
 * <li>{@code temperature}: Optional float, sampling temperature (default 1.0f).</li>
 * </ul>
 *
 * <p>
 * <b>Python Model Input/Output Expectations:</b>
 * </p>
 * <ul>
 * <li><b>Input</b>: String prompt. Models must support text-generation pipeline (e.g., GPT-2, Llama).</li>
 * <li><b>Output</b>: String generated text (extracted from pipeline result). Compatible models return this format via
 * HF pipeline.</li>
 * </ul>
 * <p>
 * To ensure model interchangeability, use generative models.
 * </p>
 */
public class TextGenerationPredictor extends AbstractTaskPredictor {
    public TextGenerationPredictor(HuggingFaceEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected String getPythonScript() {
        String doSample = config.getTemperature() > 0 ? "True" : "False";
        float temperature = config.getTemperature() > 0 ? config.getTemperature() : 1.0f;
        return loadPythonScript("text_generation.py", config.getModelId(), config.getRevision(), config.getDevice(),
                config.getMaxTokens(), doSample,
                temperature);
    }

    @Override
    protected Input prepareInput(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        if (!(body instanceof String)) {
            throw new IllegalArgumentException("Body must be String for TEXT_GENERATION");
        }
        Input input = new Input();
        input.add("data", ((String) body).getBytes(StandardCharsets.UTF_8));
        return input;
    }

    @Override
    protected void processOutput(Exchange exchange, Output output) {
        String result = output.getAsString("data");
        exchange.getMessage().setBody(result);
        exchange.getMessage().setHeader(HuggingFaceConstants.OUTPUT, result);
    }
}
