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
package org.apache.camel.component.torchserve.producer;

import java.util.List;
import java.util.Optional;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.torchserve.TorchServeConfiguration;
import org.apache.camel.component.torchserve.TorchServeConstants;
import org.apache.camel.component.torchserve.TorchServeEndpoint;
import org.apache.camel.component.torchserve.client.Management;
import org.apache.camel.component.torchserve.client.model.ApiException;
import org.apache.camel.component.torchserve.client.model.ModelDetail;
import org.apache.camel.component.torchserve.client.model.ModelList;
import org.apache.camel.component.torchserve.client.model.RegisterOptions;
import org.apache.camel.component.torchserve.client.model.Response;
import org.apache.camel.component.torchserve.client.model.ScaleWorkerOptions;
import org.apache.camel.component.torchserve.client.model.UnregisterOptions;

public class ManagementProducer extends TorchServeProducer {

    private final Management management;
    private final String operation;

    public ManagementProducer(TorchServeEndpoint endpoint) {
        super(endpoint);
        this.management = endpoint.getClient().management();
        this.operation = endpoint.getOperation();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (this.operation) {
            case "register":
                register(exchange);
                break;
            case "scale-worker":
                scaleWorker(exchange);
                break;
            case "describe":
                describe(exchange);
                break;
            case "unregister":
                unregister(exchange);
                break;
            case "list":
                list(exchange);
                break;
            case "set-default":
                setDefault(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + this.operation);
        }
    }

    private void register(Exchange exchange) throws ApiException {
        Message message = exchange.getMessage();
        TorchServeConfiguration configuration = getEndpoint().getConfiguration();
        String url = Optional
                .ofNullable(message.getHeader(TorchServeConstants.URL, String.class))
                .orElse(configuration.getUrl());
        RegisterOptions options = Optional
                .ofNullable(message.getHeader(TorchServeConstants.REGISTER_OPTIONS, RegisterOptions.class))
                .or(() -> Optional.ofNullable(configuration.getRegisterOptions()))
                .orElse(RegisterOptions.empty());
        Response response = this.management.registerModel(url, options);
        message.setBody(response.getStatus());
    }

    private void scaleWorker(Exchange exchange) throws ApiException {
        Message message = exchange.getMessage();
        TorchServeConfiguration configuration = getEndpoint().getConfiguration();
        String modelName = Optional
                .ofNullable(message.getHeader(TorchServeConstants.MODEL_NAME, String.class))
                .orElse(configuration.getModelName());
        ScaleWorkerOptions options = Optional
                .ofNullable(message.getHeader(TorchServeConstants.SCALE_WORKER_OPTIONS, ScaleWorkerOptions.class))
                .or(() -> Optional.ofNullable(configuration.getScaleWorkerOptions()))
                .orElse(ScaleWorkerOptions.empty());
        Response response = this.management.setAutoScale(modelName, options);
        message.setBody(response.getStatus());
    }

    private void describe(Exchange exchange) throws ApiException {
        Message message = exchange.getMessage();
        TorchServeConfiguration configuration = getEndpoint().getConfiguration();
        String modelName = Optional
                .ofNullable(message.getHeader(TorchServeConstants.MODEL_NAME, String.class))
                .orElse(configuration.getModelName());
        String modelVersion = Optional
                .ofNullable(message.getHeader(TorchServeConstants.MODEL_VERSION, String.class))
                .orElse(configuration.getModelVersion());
        List<ModelDetail> response;
        if (modelVersion == null) {
            response = this.management.describeModel(modelName);
        } else {
            response = this.management.describeModel(modelName, modelVersion);
        }
        message.setBody(response);
    }

    private void unregister(Exchange exchange) throws ApiException {
        Message message = exchange.getMessage();
        TorchServeConfiguration configuration = getEndpoint().getConfiguration();
        String modelName = Optional
                .ofNullable(message.getHeader(TorchServeConstants.MODEL_NAME, String.class))
                .orElse(configuration.getModelName());
        String modelVersion = Optional
                .ofNullable(message.getHeader(TorchServeConstants.MODEL_VERSION, String.class))
                .orElse(configuration.getModelVersion());
        UnregisterOptions options = Optional
                .ofNullable(message.getHeader(TorchServeConstants.UNREGISTER_OPTIONS, UnregisterOptions.class))
                .or(() -> Optional.ofNullable(configuration.getUnregisterOptions()))
                .orElse(UnregisterOptions.empty());
        Response response;
        if (modelVersion == null) {
            response = this.management.unregisterModel(modelName, options);
        } else {
            response = this.management.unregisterModel(modelName, modelVersion, options);
        }
        message.setBody(response.getStatus());
    }

    private void list(Exchange exchange) throws ApiException {
        Message message = exchange.getMessage();
        TorchServeConfiguration configuration = getEndpoint().getConfiguration();
        int limit = Optional
                .ofNullable(message.getHeader(TorchServeConstants.LIST_LIMIT, Integer.class))
                .orElse(configuration.getListLimit());
        String nextPageToken = Optional
                .ofNullable(message.getHeader(TorchServeConstants.LIST_NEXT_PAGE_TOKEN, String.class))
                .orElse(configuration.getListNextPageToken());
        ModelList response = this.management.listModels(limit, nextPageToken);
        message.setBody(response);
    }

    private void setDefault(Exchange exchange) throws ApiException {
        Message message = exchange.getMessage();
        TorchServeConfiguration configuration = getEndpoint().getConfiguration();
        String modelName = Optional
                .ofNullable(message.getHeader(TorchServeConstants.MODEL_NAME, String.class))
                .orElse(configuration.getModelName());
        String modelVersion = Optional
                .ofNullable(message.getHeader(TorchServeConstants.MODEL_VERSION, String.class))
                .orElse(configuration.getModelVersion());
        Response response = this.management.setDefault(modelName, modelVersion);
        message.setBody(response.getStatus());
    }
}
