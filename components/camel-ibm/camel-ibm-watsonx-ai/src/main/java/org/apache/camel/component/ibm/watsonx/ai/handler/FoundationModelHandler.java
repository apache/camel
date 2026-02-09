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

import com.ibm.watsonx.ai.foundationmodel.FoundationModel;
import com.ibm.watsonx.ai.foundationmodel.FoundationModelResponse;
import com.ibm.watsonx.ai.foundationmodel.FoundationModelService;
import com.ibm.watsonx.ai.foundationmodel.FoundationModelTask;
import org.apache.camel.Exchange;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiEndpoint;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiOperations;

/**
 * Handler for foundation model operations (list models, list tasks).
 */
public class FoundationModelHandler extends AbstractWatsonxAiHandler {

    public FoundationModelHandler(WatsonxAiEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public WatsonxAiOperationResponse handle(Exchange exchange, WatsonxAiOperations operation) throws Exception {
        switch (operation) {
            case listModels:
                return processListModels(exchange);
            case listTasks:
                return processListTasks(exchange);
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    @Override
    public WatsonxAiOperations[] getSupportedOperations() {
        return new WatsonxAiOperations[] {
                WatsonxAiOperations.listModels,
                WatsonxAiOperations.listTasks
        };
    }

    private WatsonxAiOperationResponse processListModels(Exchange exchange) {
        // Call the service
        FoundationModelService service = endpoint.getFoundationModelService();
        FoundationModelResponse<FoundationModel> response = service.getModels();

        List<FoundationModel> models = response.resources();

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.FOUNDATION_MODELS, models);

        return WatsonxAiOperationResponse.create(models, headers);
    }

    private WatsonxAiOperationResponse processListTasks(Exchange exchange) {
        // Call the service
        FoundationModelService service = endpoint.getFoundationModelService();
        FoundationModelResponse<FoundationModelTask> response = service.getTasks();

        List<FoundationModelTask> tasks = response.resources();

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.FOUNDATION_MODEL_TASKS, tasks);

        return WatsonxAiOperationResponse.create(tasks, headers);
    }
}
