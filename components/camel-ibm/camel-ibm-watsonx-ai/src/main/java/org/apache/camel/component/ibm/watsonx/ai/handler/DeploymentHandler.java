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

import com.ibm.watsonx.ai.chat.ChatRequest;
import com.ibm.watsonx.ai.chat.ChatResponse;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.deployment.DeploymentResource;
import com.ibm.watsonx.ai.deployment.DeploymentService;
import com.ibm.watsonx.ai.deployment.FindByIdRequest;
import com.ibm.watsonx.ai.textgeneration.TextGenerationParameters;
import com.ibm.watsonx.ai.textgeneration.TextGenerationRequest;
import com.ibm.watsonx.ai.textgeneration.TextGenerationResponse;
import com.ibm.watsonx.ai.timeseries.ForecastData;
import com.ibm.watsonx.ai.timeseries.ForecastResponse;
import com.ibm.watsonx.ai.timeseries.InputSchema;
import com.ibm.watsonx.ai.timeseries.TimeSeriesRequest;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConfiguration;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiEndpoint;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiOperations;

/**
 * Handler for deployment operations.
 */
public class DeploymentHandler extends AbstractWatsonxAiHandler {

    public DeploymentHandler(WatsonxAiEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public WatsonxAiOperationResponse handle(Exchange exchange, WatsonxAiOperations operation) throws Exception {
        switch (operation) {
            case deploymentInfo:
                return processDeploymentInfo(exchange);
            case deploymentGenerate:
                return processDeploymentGenerate(exchange);
            case deploymentChat:
                return processDeploymentChat(exchange);
            case deploymentForecast:
                return processDeploymentForecast(exchange);
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    @Override
    public WatsonxAiOperations[] getSupportedOperations() {
        return new WatsonxAiOperations[] {
                WatsonxAiOperations.deploymentInfo,
                WatsonxAiOperations.deploymentGenerate,
                WatsonxAiOperations.deploymentChat,
                WatsonxAiOperations.deploymentForecast
        };
    }

    private WatsonxAiOperationResponse processDeploymentInfo(Exchange exchange) {
        Message in = exchange.getIn();
        WatsonxAiConfiguration config = getConfiguration();

        // Get deployment ID from header or configuration
        String deploymentId = in.getHeader(WatsonxAiConstants.DEPLOYMENT_ID, String.class);
        if (deploymentId == null || deploymentId.isEmpty()) {
            deploymentId = config.getDeploymentId();
        }
        if (deploymentId == null || deploymentId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Deployment ID must be provided via header '" + WatsonxAiConstants.DEPLOYMENT_ID
                                               + "' or endpoint configuration 'deploymentId'");
        }

        // Get space ID from header or configuration (required for findById)
        String spaceId = in.getHeader(WatsonxAiConstants.SPACE_ID, String.class);
        if (spaceId == null || spaceId.isEmpty()) {
            spaceId = config.getSpaceId();
        }
        if (spaceId == null || spaceId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Space ID must be provided via header '" + WatsonxAiConstants.SPACE_ID
                                               + "' or endpoint configuration 'spaceId'");
        }

        // Build request
        FindByIdRequest request = FindByIdRequest.builder()
                .deploymentId(deploymentId)
                .spaceId(spaceId)
                .build();

        // Call the service
        DeploymentService service = endpoint.getDeploymentService();
        DeploymentResource response = service.findById(request);

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        if (response.metadata() != null) {
            headers.put(WatsonxAiConstants.DEPLOYMENT_NAME, response.metadata().name());
        }
        if (response.entity() != null) {
            headers.put(WatsonxAiConstants.DEPLOYMENT_ASSET_TYPE, response.entity().deployedAssetType());
            if (response.entity().status() != null) {
                headers.put(WatsonxAiConstants.DEPLOYMENT_STATUS, response.entity().status().state());
            }
        }

        return WatsonxAiOperationResponse.create(response, headers);
    }

    private WatsonxAiOperationResponse processDeploymentGenerate(Exchange exchange) {
        Message in = exchange.getIn();
        WatsonxAiConfiguration config = getConfiguration();

        // Get deployment ID from header or configuration
        String deploymentId = in.getHeader(WatsonxAiConstants.DEPLOYMENT_ID, String.class);
        if (deploymentId == null || deploymentId.isEmpty()) {
            deploymentId = config.getDeploymentId();
        }
        if (deploymentId == null || deploymentId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Deployment ID must be provided via header '" + WatsonxAiConstants.DEPLOYMENT_ID
                                               + "' or endpoint configuration 'deploymentId'");
        }

        // Get input from body or header
        String input = getInput(exchange);

        // Build parameters from configuration and headers
        TextGenerationParameters.Builder paramsBuilder = TextGenerationParameters.builder();
        applyTextGenerationParameters(paramsBuilder, exchange);

        // Build request
        TextGenerationRequest request = TextGenerationRequest.builder()
                .deploymentId(deploymentId)
                .input(input)
                .parameters(paramsBuilder.build())
                .build();

        // Call the service
        DeploymentService service = endpoint.getDeploymentService();
        TextGenerationResponse response = service.generate(request);

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.GENERATED_TEXT, response.toText());
        if (!response.results().isEmpty()) {
            TextGenerationResponse.Result result = response.results().get(0);
            headers.put(WatsonxAiConstants.INPUT_TOKEN_COUNT, result.inputTokenCount());
            headers.put(WatsonxAiConstants.OUTPUT_TOKEN_COUNT, result.generatedTokenCount());
            headers.put(WatsonxAiConstants.STOP_REASON, result.stopReason());
        }

        return WatsonxAiOperationResponse.create(response.toText(), headers);
    }

    private WatsonxAiOperationResponse processDeploymentChat(Exchange exchange) {
        Message in = exchange.getIn();
        WatsonxAiConfiguration config = getConfiguration();

        // Get deployment ID from header or configuration
        String deploymentId = in.getHeader(WatsonxAiConstants.DEPLOYMENT_ID, String.class);
        if (deploymentId == null || deploymentId.isEmpty()) {
            deploymentId = config.getDeploymentId();
        }
        if (deploymentId == null || deploymentId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Deployment ID must be provided via header '" + WatsonxAiConstants.DEPLOYMENT_ID
                                               + "' or endpoint configuration 'deploymentId'");
        }

        // Get messages from header or body
        List<ChatMessage> messages = getMessages(exchange);

        // Build request - deployment chat API only supports 'messages' and optionally
        // 'context', 'tools', 'tool_choice', 'tool_choice_option' - no parameters allowed
        ChatRequest request = ChatRequest.builder()
                .deploymentId(deploymentId)
                .messages(messages)
                .build();

        // Call the service
        DeploymentService service = endpoint.getDeploymentService();
        ChatResponse response = service.chat(request);

        String content = response.toAssistantMessage().content();

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.GENERATED_TEXT, content);
        if (response.usage() != null) {
            headers.put(WatsonxAiConstants.INPUT_TOKEN_COUNT, response.usage().promptTokens());
            headers.put(WatsonxAiConstants.OUTPUT_TOKEN_COUNT, response.usage().completionTokens());
        }
        if (response.finishReason() != null) {
            headers.put(WatsonxAiConstants.STOP_REASON, response.finishReason().value());
        }

        return WatsonxAiOperationResponse.create(content, headers);
    }

    private WatsonxAiOperationResponse processDeploymentForecast(Exchange exchange) {
        Message in = exchange.getIn();
        WatsonxAiConfiguration config = getConfiguration();

        // Get deployment ID from header or configuration
        String deploymentId = in.getHeader(WatsonxAiConstants.DEPLOYMENT_ID, String.class);
        if (deploymentId == null || deploymentId.isEmpty()) {
            deploymentId = config.getDeploymentId();
        }
        if (deploymentId == null || deploymentId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Deployment ID must be provided via header '" + WatsonxAiConstants.DEPLOYMENT_ID
                                               + "' or endpoint configuration 'deploymentId'");
        }

        // Get input schema and data from headers or body
        InputSchema inputSchema = in.getHeader(WatsonxAiConstants.FORECAST_INPUT_SCHEMA, InputSchema.class);
        ForecastData forecastData = in.getHeader(WatsonxAiConstants.FORECAST_DATA, ForecastData.class);

        // If not in headers, try to get from body as TimeSeriesRequest
        if (inputSchema == null || forecastData == null) {
            Object body = in.getBody();
            if (body instanceof TimeSeriesRequest request) {
                inputSchema = request.inputSchema();
                forecastData = request.data();
            }
        }

        if (inputSchema == null) {
            throw new IllegalArgumentException(
                    "Input schema must be provided via header '" + WatsonxAiConstants.FORECAST_INPUT_SCHEMA
                                               + "' or as part of TimeSeriesRequest in body");
        }
        if (forecastData == null) {
            throw new IllegalArgumentException(
                    "Forecast data must be provided via header '" + WatsonxAiConstants.FORECAST_DATA
                                               + "' or as part of TimeSeriesRequest in body");
        }

        // Build request with deployment ID
        TimeSeriesRequest request = TimeSeriesRequest.builder()
                .deploymentId(deploymentId)
                .inputSchema(inputSchema)
                .data(forecastData)
                .build();

        // Call the service
        DeploymentService service = endpoint.getDeploymentService();
        ForecastResponse response = service.forecast(request);

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.FORECAST_RESULTS, response.results());
        headers.put(WatsonxAiConstants.FORECAST_INPUT_DATA_POINTS, response.inputDataPoints());
        headers.put(WatsonxAiConstants.FORECAST_OUTPUT_DATA_POINTS, response.outputDataPoints());
        headers.put(WatsonxAiConstants.MODEL_ID, response.modelId());

        return WatsonxAiOperationResponse.create(response.results(), headers);
    }
}
