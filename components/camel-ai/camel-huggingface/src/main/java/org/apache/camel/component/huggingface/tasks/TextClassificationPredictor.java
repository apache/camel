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
import java.util.ArrayList;
import java.util.List;

import ai.djl.modality.Classifications;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.huggingface.HuggingFaceConfiguration;
import org.apache.camel.component.huggingface.HuggingFaceConstants;

/**
 * Predictor for the TEXT_CLASSIFICATION task, performing sentiment or label classification on text.
 *
 * <p>
 * This predictor uses Hugging Face's text-classification pipeline to assign labels and scores to input text. It is
 * designed for tasks like sentiment analysis, toxicity detection, or multi-class categorization.
 * </p>
 *
 * <p>
 * <b>Input Contract (Camel Message Body):</b>
 * </p>
 * <ul>
 * <li>{@code String}: The text to classify (e.g., "I love this movie!").</li>
 * </ul>
 *
 * <p>
 * <b>Output Contract (Camel Message Body):</b>
 * </p>
 * <ul>
 * <li>{@code ai.djl.modality.Classifications}: DJL Classifications object containing label/score pairs (e.g.,
 * "POSITIVE" with score 0.99).</li>
 * </ul>
 *
 * <p>
 * <b>Camel Headers Used:</b>
 * </p>
 * <ul>
 * <li>{@code HuggingFaceConstants.OUTPUT}: Full JSON result string (for raw access).</li>
 * </ul>
 *
 * <p>
 * <b>Relevant HuggingFaceConfiguration Properties:</b>
 * </p>
 * <ul>
 * <li>{@code modelId}: Required String, e.g., "distilbert-base-uncased-finetuned-sst-2-english" (sentiment) or custom
 * classifier.</li>
 * <li>{@code revision}: Optional String, model revision (default "main").</li>
 * <li>{@code device}: Optional String, inference device (default "auto").</li>
 * </ul>
 *
 * <p>
 * <b>Python Model Input/Output Expectations:</b>
 * </p>
 * <ul>
 * <li><b>Input</b>: String text. Models must support text-classification pipeline (e.g., sentiment or multi-label
 * classifiers).</li>
 * <li><b>Output</b>: JSON list of dicts with "label" (string) and "score" (float). Compatible models return this format
 * via HF pipeline.</li>
 * </ul>
 * <p>
 * To ensure model interchangeability, use classification-tuned models. For multi-class, scores may sum to 1.
 * </p>
 */
public class TextClassificationPredictor extends AbstractTaskPredictor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public TextClassificationPredictor(HuggingFaceConfiguration config) {
        super(config);
    }

    @Override
    protected String getPythonScript() {
        return """
                from transformers import pipeline
                from djl_python import Input, Output
                import json
                import torch
                import sys
                import logging

                pipe = pipeline(
                    task='text-classification',
                    model='%s',
                    revision='%s',
                    device_map='%s',
                    top_k=%s
                )

                def handle(inputs: Input):
                    try:
                        if inputs.content.size() == 0:
                            logging.info("Handling warmup call - returning empty output")
                            return Output()
                        input_str = inputs.get_as_string("data")
                        torch.manual_seed(42)
                        result = pipe(input_str)
                        # If result is a list of lists, take the first one
                        if result and isinstance(result[0], list):
                            result = result[0]
                        outputs = Output()
                        outputs.add(json.dumps(result), "data")
                        return outputs
                    except Exception as e:
                        logging.error("Error in handle function: " + str(e))
                        outputs = Output()
                        outputs.add(json.dumps({"error": str(e)}), "data")
                        return outputs
                """.formatted(config.getModelId(), config.getRevision(), config.getDevice(), config.getTopK());
    }

    @Override
    protected Input prepareInput(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        if (!(body instanceof String)) {
            throw new IllegalArgumentException("Body must be String for TEXT_CLASSIFICATION");
        }
        Input input = new Input();
        input.add("data", ((String) body).getBytes(StandardCharsets.UTF_8));
        return input;
    }

    @Override
    protected void processOutput(Exchange exchange, Output output) throws Exception {
        String resultJson = output.getAsString("data");
        if (resultJson.contains("\"error\"")) {
            throw new RuntimeCamelException("Python inference failed: " + resultJson);
        }
        exchange.getMessage().setHeader(HuggingFaceConstants.OUTPUT, resultJson);
        JsonNode node = objectMapper.readTree(resultJson);
        List<String> classNames = new ArrayList<>();
        List<Double> probabilities = new ArrayList<>();
        for (JsonNode item : node) {
            String label = item.get("label").asText();
            classNames.add(label);
            double score = item.get("score").asDouble();
            probabilities.add(score);
        }
        Classifications classifications = new Classifications(classNames, probabilities);
        exchange.getMessage().setBody(classifications);
    }
}
