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
import java.util.List;

import ai.djl.modality.Input;
import ai.djl.modality.Output;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.huggingface.HuggingFaceConstants;
import org.apache.camel.component.huggingface.HuggingFaceEndpoint;

/**
 * Predictor for the SENTENCE_EMBEDDINGS task, generating vector embeddings from input text.
 *
 * <p>
 * This predictor uses the <b>Sentence Transformers</b> library to produce high-quality, dense vector representations
 * (embeddings) of text. It is designed for tasks like semantic similarity search, clustering, recommendation systems,
 * or as input to downstream ML models (e.g., for classification or RAG).
 * </p>
 *
 * <p>
 * <b>Input Contract (Camel Message Body):</b>
 * </p>
 * <ul>
 * <li>{@code String}: A single text to embed.</li>
 * <li>{@code String[]}: An array of texts to embed (batch processing).</li>
 * <li>{@code List<String>}: A list of texts to embed (batch processing).</li>
 * </ul>
 *
 * <p>
 * <b>Output Contract (Camel Message Body):</b>
 * </p>
 * <ul>
 * <li>{@code float[][]}: The embedding tensor as a 2D float array (batch size × embedding dimension). For a single
 * input, the batch size is 1.</li>
 * </ul>
 *
 * <p>
 * <b>Camel Headers Used:</b>
 * </p>
 * <ul>
 * <li>{@code HuggingFaceConstants.OUTPUT}: Full JSON result string (e.g., [[0.1, 0.2, ...]] for the embedding tensor as
 * list).</li>
 * </ul>
 *
 * <p>
 * <b>Relevant HuggingFaceConfiguration Properties:</b>
 * </p>
 * <ul>
 * <li>{@code modelId}: Required String, e.g., "sentence-transformers/all-MiniLM-L6-v2".</li>
 * <li>{@code revision}: Optional String, model revision (default "main").</li>
 * <li>{@code device}: Optional String, inference device (default "auto").</li>
 * </ul>
 *
 * <p>
 * <b>Python Model Input/Output Expectations:</b>
 * </p>
 * <ul>
 * <li><b>Input</b>: JSON array of strings (e.g., ["text1", "text2"]). Models must support sentence-transformers.</li>
 * <li><b>Output</b>: JSON list of lists of floats (embedding tensor as list; dimension depends on model, e.g., 384 or
 * 768). Compatible models return this format via SentenceTransformer.encode().</li>
 * </ul>
 * <p>
 * To ensure model interchangeability, use embedding-tuned models. Embeddings can be used for cosine similarity or
 * vector search.
 * </p>
 */
public class SentenceEmbeddingsPredictor extends AbstractTaskPredictor {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public SentenceEmbeddingsPredictor(HuggingFaceEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected String getRequirements() {
        return """
                torch>=2.0.0
                accelerate
                sentence-transformers>=2.2.0
                """;
    }

    @Override
    protected String getPythonScript() {
        return loadPythonScript("sentence_embeddings.py", config.getDevice(), config.getModelId());
    }

    @Override
    protected Input prepareInput(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        String[] inputArray;
        if (body instanceof String) {
            inputArray = new String[] { (String) body };
        } else if (body instanceof String[]) {
            inputArray = (String[]) body;
        } else if (body instanceof List) {
            inputArray = ((List<String>) body).toArray(new String[0]);
        } else {
            throw new IllegalArgumentException(
                    "Body must be String, String[], or List<String> for SENTENCE_EMBEDDINGS");
        }

        String jsonInput = objectMapper.writeValueAsString(inputArray);
        Input input = new Input();
        input.add("data", jsonInput.getBytes(StandardCharsets.UTF_8));
        return input;
    }

    @Override
    protected void processOutput(Exchange exchange, Output output) throws Exception {
        String resultJson = output.getAsString("data");

        if (resultJson.contains("\"error\"")) {
            throw new RuntimeCamelException("Python inference failed: " + resultJson);
        }

        // Parse JSON (list of lists)
        JsonNode node = objectMapper.readTree(resultJson);

        // Convert to float[][] (batch × embedding_dim)
        float[][] embeddings = objectMapper.convertValue(node, float[][].class);

        if (embeddings == null || embeddings.length == 0) {
            throw new RuntimeCamelException("No embedding data returned from inference");
        }

        exchange.getMessage().setBody(embeddings);
        exchange.getMessage().setHeader(HuggingFaceConstants.OUTPUT, resultJson);
    }
}
