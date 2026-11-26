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

import com.ibm.watsonx.ai.chat.TextChatResponse;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ChatParameters;
import com.ibm.watsonx.ai.deployment.DeploymentChatRequest;
import com.ibm.watsonx.ai.deployment.DeploymentResource;
import com.ibm.watsonx.ai.deployment.DeploymentService;
import com.ibm.watsonx.ai.deployment.FindByIdRequest;
import com.ibm.watsonx.ai.textgeneration.TextGenerationParameters;
import com.ibm.watsonx.ai.textgeneration.TextGenerationRequest;
import com.ibm.watsonx.ai.textgeneration.TextGenerationResponse;
import com.ibm.watsonx.ai.timeseries.ForecastResponse;
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

        // The deployment is looked up in its space (header or configuration), or else in the project
        String spaceId = in.getHeader(WatsonxAiConstants.SPACE_ID, String.class);
        if (spaceId == null || spaceId.isEmpty()) {
            spaceId = config.getSpaceId();
        }
        FindByIdRequest.Builder request = FindByIdRequest.builder().deploymentId(deploymentId);
        if (spaceId != null && !spaceId.isEmpty()) {
            request.spaceId(spaceId);
        } else if (config.getProjectId() != null && !config.getProjectId().isEmpty()) {
            request.projectId(config.getProjectId());
        } else {
            throw new IllegalArgumentException(
                    "Space ID must be provided via header '" + WatsonxAiConstants.SPACE_ID
                                               + "' or endpoint configuration 'spaceId', or a project via 'projectId'");
        }

        // Call the service
        DeploymentService service = endpoint.getDeploymentService();
        DeploymentResource response = service.findById(request.build());

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

    @SuppressWarnings("unchecked")
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

        // The parameters of the deployment chat API (the deployment gives the model); a prompt template deployment
        // may fix some of them
        ChatParameters.Builder parameters = ChatParameters.builder();
        applyChatParameters(parameters, exchange);
        DeploymentChatRequest request = DeploymentChatRequest.builder()
                .deploymentId(deploymentId)
                .messages(messages)
                .tools(in.getHeader(WatsonxAiConstants.TOOLS, List.class))
                .parameters(parameters.build())
                .thinking(buildThinking(exchange))
                .build();

        // Call the service
        DeploymentService service = endpoint.getDeploymentService();
        TextChatResponse response = service.chat(request);

        Map<String, Object> headers = chatResponseHeaders(response, messages);
        Object content = headers.get(WatsonxAiConstants.GENERATED_TEXT);
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

        TimeSeriesRequest request = getTimeSeriesRequest(exchange, deploymentId, true);

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
