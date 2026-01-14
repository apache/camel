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
package org.apache.camel.component.ibm.watsonx.ai.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.ibm.watsonx.ai.embedding.EmbeddingParameters;
import com.ibm.watsonx.ai.embedding.EmbeddingResponse;
import com.ibm.watsonx.ai.embedding.EmbeddingService;
import org.apache.camel.Exchange;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConfiguration;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiEndpoint;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiOperations;

/**
 * Handler for embedding operations.
 */
public class EmbeddingHandler extends AbstractWatsonxAiHandler {

    public EmbeddingHandler(WatsonxAiEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public WatsonxAiOperationResponse handle(Exchange exchange, WatsonxAiOperations operation) throws Exception {
        if (operation != WatsonxAiOperations.embedding) {
            throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
        return processEmbedding(exchange);
    }

    @Override
    public WatsonxAiOperations[] getSupportedOperations() {
        return new WatsonxAiOperations[] { WatsonxAiOperations.embedding };
    }

    private WatsonxAiOperationResponse processEmbedding(Exchange exchange) {
        WatsonxAiConfiguration config = getConfiguration();

        // Get inputs from body or header
        List<String> inputs = getInputs(exchange);

        // Build parameters from configuration
        EmbeddingParameters.Builder paramsBuilder = EmbeddingParameters.builder();

        if (config.getTruncateInputTokens() != null) {
            paramsBuilder.truncateInputTokens(config.getTruncateInputTokens());
        }

        // Call the service
        EmbeddingService service = endpoint.getEmbeddingService();
        EmbeddingResponse response = service.embedding(inputs, paramsBuilder.build());

        // Extract embedding vectors from response
        List<List<Float>> embeddings = response.results().stream()
                .map(EmbeddingResponse.Result::embedding)
                .collect(Collectors.toList());

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.EMBEDDINGS, embeddings);
        headers.put(WatsonxAiConstants.INPUT_TOKEN_COUNT, response.inputTokenCount());
        headers.put(WatsonxAiConstants.MODEL_ID, response.modelId());

        return WatsonxAiOperationResponse.create(embeddings, headers);
    }
}
