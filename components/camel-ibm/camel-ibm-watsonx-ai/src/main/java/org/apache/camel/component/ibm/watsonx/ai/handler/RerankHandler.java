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

import com.ibm.watsonx.ai.rerank.RerankParameters;
import com.ibm.watsonx.ai.rerank.RerankResponse;
import com.ibm.watsonx.ai.rerank.RerankService;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConfiguration;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiEndpoint;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiOperations;

/**
 * Handler for rerank operations.
 */
public class RerankHandler extends AbstractWatsonxAiHandler {

    public RerankHandler(WatsonxAiEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public WatsonxAiOperationResponse handle(Exchange exchange, WatsonxAiOperations operation) throws Exception {
        if (operation != WatsonxAiOperations.rerank) {
            throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
        return processRerank(exchange);
    }

    @Override
    public WatsonxAiOperations[] getSupportedOperations() {
        return new WatsonxAiOperations[] { WatsonxAiOperations.rerank };
    }

    private WatsonxAiOperationResponse processRerank(Exchange exchange) {
        Message in = exchange.getIn();
        WatsonxAiConfiguration config = getConfiguration();

        // Get query from header (required for rerank)
        String query = in.getHeader(WatsonxAiConstants.RERANK_QUERY, String.class);
        if (query == null || query.isEmpty()) {
            throw new IllegalArgumentException(
                    "Rerank query must be provided via header '" + WatsonxAiConstants.RERANK_QUERY + "'");
        }

        // Get inputs from body or header
        List<String> inputs = getInputs(exchange);

        // Build parameters from configuration and headers
        RerankParameters.Builder paramsBuilder = RerankParameters.builder();

        Integer topN = in.getHeader(WatsonxAiConstants.RERANK_TOP_N, Integer.class);
        if (topN == null) {
            topN = config.getRerankTopN();
        }
        if (topN != null) {
            paramsBuilder.topN(topN);
            // TODO: remove the following with the next sdk release
            paramsBuilder.inputs(config.getReturnDocuments() != null ? config.getReturnDocuments() : false);
        } else if (config.getReturnDocuments() != null) {
            paramsBuilder.inputs(config.getReturnDocuments());
        }

        // Call the service
        RerankService service = endpoint.getRerankService();
        RerankResponse response = service.rerank(query, inputs, paramsBuilder.build());

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.INPUT_TOKEN_COUNT, response.inputTokenCount());
        headers.put(WatsonxAiConstants.MODEL_ID, response.modelId());

        return WatsonxAiOperationResponse.create(response.results(), headers);
    }
}
