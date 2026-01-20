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
import java.util.Map;

import com.ibm.watsonx.ai.tokenization.TokenizationParameters;
import com.ibm.watsonx.ai.tokenization.TokenizationResponse;
import com.ibm.watsonx.ai.tokenization.TokenizationService;
import org.apache.camel.Exchange;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiEndpoint;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiOperations;

/**
 * Handler for tokenization operations.
 */
public class TokenizationHandler extends AbstractWatsonxAiHandler {

    public TokenizationHandler(WatsonxAiEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public WatsonxAiOperationResponse handle(Exchange exchange, WatsonxAiOperations operation) throws Exception {
        if (operation != WatsonxAiOperations.tokenize) {
            throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
        return processTokenize(exchange);
    }

    @Override
    public WatsonxAiOperations[] getSupportedOperations() {
        return new WatsonxAiOperations[] { WatsonxAiOperations.tokenize };
    }

    private WatsonxAiOperationResponse processTokenize(Exchange exchange) {
        // Get input from body or header
        String input = getInput(exchange);

        // Build parameters from configuration
        TokenizationParameters.Builder paramsBuilder = TokenizationParameters.builder();

        // By default, return tokens to get the token list
        paramsBuilder.returnTokens(true);

        // Call the service
        TokenizationService service = endpoint.getTokenizationService();
        TokenizationResponse response = service.tokenize(input, paramsBuilder.build());

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.TOKEN_COUNT, response.result().tokenCount());
        headers.put(WatsonxAiConstants.TOKENS, response.result().tokens());
        headers.put(WatsonxAiConstants.MODEL_ID, response.modelId());

        return WatsonxAiOperationResponse.create(response.result().tokenCount(), headers);
    }
}
