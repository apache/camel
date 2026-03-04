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
import java.util.HashMap;
import java.util.Map;

import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.modality.nlp.qa.QAInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.component.huggingface.HuggingFaceConstants;
import org.apache.camel.component.huggingface.HuggingFaceEndpoint;

/**
 * Predictor for the QUESTION_ANSWERING task, extracting answers from a given context.
 *
 * <p>
 * This predictor uses Hugging Face's question-answering pipeline to find and score answers within a provided text
 * context. It is designed for tasks like knowledge extraction, Q&A over documents, or information retrieval from
 * passages.
 * </p>
 *
 * <p>
 * <b>Input Contract (Camel Message Body):</b>
 * </p>
 * <ul>
 * <li>{@link ai.djl.modality.nlp.qa.QAInput}</li>
 * </ul>
 *
 * <p>
 * <b>Output Contract (Camel Message Body):</b>
 * </p>
 * <ul>
 * <li>{@code String}: model's answer.</li>
 * </ul>
 *
 * <p>
 * <b>Camel Headers Used:</b>
 * </p>
 * <ul>
 * <li>{@code HuggingFaceConstants.OUTPUT}: {@code String} with the model's answer.</li>
 * </ul>
 *
 * <p>
 * <b>Relevant HuggingFaceConfiguration Properties:</b>
 * </p>
 * <ul>
 * <li>{@code modelId}: Required String, e.g., "distilbert-base-cased-distilled-squad".</li>
 * <li>{@code revision}: Optional String, model revision (default "main").</li>
 * <li>{@code device}: Optional String, inference device (default "auto").</li>
 * </ul>
 *
 * <p>
 * <b>Python Model Input/Output Expectations:</b>
 * </p>
 * <ul>
 * <li><b>Input</b>: JSON array of strings [question, context]. Models must support question-answering pipeline (e.g.,
 * distilbert-squad).</li>
 * <li><b>Output</b>: JSON dict with "answer" (string), "score" (float), "start" (int), "end" (int). Compatible models
 * return this format via HF pipeline.</li>
 * </ul>
 * <p>
 * To ensure model interchangeability, use QA-tuned models.
 * </p>
 */
public class QuestionAnsweringPredictor extends AbstractTaskPredictor {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QuestionAnsweringPredictor(HuggingFaceEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected String getPythonScript() {
        return loadPythonScript("question_answering.py", config.getModelId(), config.getRevision(), config.getDevice());
    }

    @Override
    protected Input prepareInput(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        QAInput qaInput;
        if (body instanceof QAInput) {
            qaInput = (QAInput) body;
        } else {
            throw new IllegalArgumentException("Body must be ai.djl.modality.nlp.qa.QAInput for QUESTION_ANSWERING");
        }

        // Serialize QAInput to JSON for Python
        Map<String, String> inputMap = new HashMap<>();
        inputMap.put("question", qaInput.getQuestion());
        inputMap.put("context", qaInput.getParagraph());
        String jsonInput = objectMapper.writeValueAsString(inputMap);

        Input input = new Input();
        input.add("data", jsonInput.getBytes(StandardCharsets.UTF_8));
        return input;
    }

    @Override
    protected void processOutput(Exchange exchange, Output output) {
        String result = output.getAsString("data");
        exchange.getMessage().setBody(result);
        exchange.getMessage().setHeader(HuggingFaceConstants.OUTPUT, result);
    }
}
