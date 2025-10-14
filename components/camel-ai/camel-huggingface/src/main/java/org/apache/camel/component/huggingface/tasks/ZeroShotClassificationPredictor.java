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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import ai.djl.modality.Classifications;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.huggingface.HuggingFaceConstants;
import org.apache.camel.component.huggingface.HuggingFaceEndpoint;

/**
 * Predictor for the ZERO_SHOT_CLASSIFICATION task, performing label classification on text without prior training.
 *
 * <p>
 * This predictor uses Hugging Face's zero-shot classification pipeline to classify text into user-provided candidate
 * labels. It is designed for tasks like sentiment analysis, topic detection, or custom categorization without
 * fine-tuning.
 * </p>
 *
 * <p>
 * <b>Input Contract (Camel Message Body): </b>
 * </p>
 * <ul>
 * <li>{@code List<String>} or {@code String[]}: where [0] is the text to classify, [1+] are candidate labels (e.g., ["I
 * love this movie!", "positive", "negative"]).</li>
 * </ul>
 *
 * <p>
 * <b>Output Contract (Camel Message Body):</b>
 * </p>
 * <ul>
 * <li>{@code String} best label or {@code ai.djl.modality.Classifications} if autoSelect is false.</li>
 * </ul>
 *
 * <p>
 * <b>Camel Headers Used:</b>
 * </p>
 * <ul>
 * <li>{@code HuggingFaceConstants.OUTPUT}: The raw JSON result string from the pipeline.</li>
 * </ul>
 *
 * <p>
 * <b>Relevant HuggingFaceConfiguration Properties:</b>
 * </p>
 * <ul>
 * <li>{@code modelId}: Required String, e.g., "facebook/bart-large-mnli" (zero-shot classification model).</li>
 * <li>{@code revision}: Optional String, model revision (default "main").</li>
 * <li>{@code device}: Optional String, inference device (default "auto").</li>
 * <li>{@code multiLabel}: Optional boolean, allow multi-label classifications (default false).</li>
 * <li>{@code autoSelect}: Optional boolean, auto-select best label (default true).</li>
 * </ul>
 *
 * <p>
 * <b>Python Model Input/Output Expectations:</b>
 * </p>
 * <ul>
 * <li><b>Input</b>: JSON array of strings [text, label1, label2, ...]. Models must support zero-shot classification
 * (e.g., NLI-based like bart-large-mnli).</li>
 * <li><b>Output</b>: Best label or JSON dict with "sequence" (text), "labels" (sorted by score), "scores"
 * (probabilities) if autoSelect is false. Compatible models return this format via HF pipeline.</li>
 * </ul>
 * <p>
 * To ensure model interchangeability, use NLI-tuned models.
 * </p>
 */
public class ZeroShotClassificationPredictor extends AbstractTaskPredictor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ZeroShotClassificationPredictor(HuggingFaceEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected String getPythonScript() {
        String multiLabel = config.isMultiLabel() ? "True" : "False";
        String autoSelect = config.isAutoSelect() ? "True" : "False";
        return loadPythonScript("zero_shot_classification.py", config.getModelId(), config.getRevision(),
                config.getDevice(), multiLabel, autoSelect);
    }

    @Override
    protected Input prepareInput(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        String[] inputArray;
        if (body instanceof String[]) {
            inputArray = (String[]) body;
        } else if (body instanceof List) {
            inputArray = ((List<String>) body).toArray(new String[0]);
        } else {
            throw new IllegalArgumentException(
                    "For ZERO_SHOT_CLASSIFICATION, body must be String[] or List<String> [text, label1, label2, ...]");
        }
        if (inputArray.length < 2) {
            throw new IllegalArgumentException("Input must have at least 2 elements: [text, label1, ...]");
        }
        String jsonArray = objectMapper.writeValueAsString(inputArray);
        Input input = new Input();
        input.add("data", jsonArray.getBytes("UTF-8"));
        return input;
    }

    @Override
    protected void processOutput(Exchange exchange, Output output) throws Exception {
        String result = output.getAsString("data");
        if (result.contains("\"error\"")) {
            throw new RuntimeCamelException("Python inference failed: " + result);
        }
        exchange.getMessage().setHeader(HuggingFaceConstants.OUTPUT, result);
        if (config.isAutoSelect()) {
            exchange.getMessage().setBody(result);
        } else {
            JsonNode root = objectMapper.readTree(result);
            List<String> labels = StreamSupport.stream(root.get("labels").spliterator(), false)
                    .map(JsonNode::asText)
                    .collect(Collectors.toList());
            List<Double> scores = StreamSupport.stream(root.get("scores").spliterator(), false)
                    .map(JsonNode::asDouble)
                    .collect(Collectors.toList());
            Classifications classifications = new Classifications(labels, scores);
            exchange.getMessage().setBody(classifications);
        }
    }
}
