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

import java.util.EnumMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiEndpoint;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiOperations;

/**
 * Manager that routes operations to the appropriate handler.
 */
public class WatsonxAiOperationManager {

    private final Map<WatsonxAiOperations, WatsonxAiOperationHandler> handlerMap = new EnumMap<>(WatsonxAiOperations.class);

    public WatsonxAiOperationManager(WatsonxAiEndpoint endpoint) {
        registerHandlers(endpoint);
    }

    private void registerHandlers(WatsonxAiEndpoint endpoint) {
        // Text Generation
        TextGenerationHandler textGenerationHandler = new TextGenerationHandler(endpoint);
        registerHandler(textGenerationHandler);

        // Chat
        ChatHandler chatHandler = new ChatHandler(endpoint);
        registerHandler(chatHandler);

        // Embedding
        EmbeddingHandler embeddingHandler = new EmbeddingHandler(endpoint);
        registerHandler(embeddingHandler);

        // Rerank
        RerankHandler rerankHandler = new RerankHandler(endpoint);
        registerHandler(rerankHandler);

        // Tokenization
        TokenizationHandler tokenizationHandler = new TokenizationHandler(endpoint);
        registerHandler(tokenizationHandler);

        // Detection
        DetectionHandler detectionHandler = new DetectionHandler(endpoint);
        registerHandler(detectionHandler);

        // Text Extraction
        TextExtractionHandler textExtractionHandler = new TextExtractionHandler(endpoint);
        registerHandler(textExtractionHandler);

        // Text Classification
        TextClassificationHandler textClassificationHandler = new TextClassificationHandler(endpoint);
        registerHandler(textClassificationHandler);

        // Forecast
        ForecastHandler forecastHandler = new ForecastHandler(endpoint);
        registerHandler(forecastHandler);

        // Foundation Model
        FoundationModelHandler foundationModelHandler = new FoundationModelHandler(endpoint);
        registerHandler(foundationModelHandler);

        // Deployment
        DeploymentHandler deploymentHandler = new DeploymentHandler(endpoint);
        registerHandler(deploymentHandler);

        // Tool
        ToolHandler toolHandler = new ToolHandler(endpoint);
        registerHandler(toolHandler);
    }

    private void registerHandler(WatsonxAiOperationHandler handler) {
        for (WatsonxAiOperations operation : handler.getSupportedOperations()) {
            handlerMap.put(operation, handler);
        }
    }

    /**
     * Process the exchange using the appropriate handler for the operation.
     *
     * @param  exchange  the current exchange
     * @param  operation the operation to perform
     * @return           the operation response
     * @throws Exception if an error occurs during processing
     */
    public WatsonxAiOperationResponse process(Exchange exchange, WatsonxAiOperations operation) throws Exception {
        WatsonxAiOperationHandler handler = handlerMap.get(operation);

        if (handler == null) {
            throw new UnsupportedOperationException("No handler for operation: " + operation);
        }

        return handler.handle(exchange, operation);
    }
}
