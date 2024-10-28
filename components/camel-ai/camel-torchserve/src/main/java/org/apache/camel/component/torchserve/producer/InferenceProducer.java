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

import java.util.Optional;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.torchserve.TorchServeConfiguration;
import org.apache.camel.component.torchserve.TorchServeConstants;
import org.apache.camel.component.torchserve.TorchServeEndpoint;
import org.apache.camel.component.torchserve.client.Inference;
import org.apache.camel.component.torchserve.client.model.ApiException;
import org.apache.camel.component.torchserve.client.model.Response;

public class InferenceProducer extends TorchServeProducer {

    private final Inference inference;
    private final String operation;

    public InferenceProducer(TorchServeEndpoint endpoint) {
        super(endpoint);
        this.inference = endpoint.getClient().inference();
        this.operation = endpoint.getOperation();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (this.operation) {
            case "ping":
                ping(exchange);
                break;
            case "predictions":
                predictions(exchange);
                break;
            case "explanations":
                explanations(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + this.operation);
        }
    }

    private void ping(Exchange exchange) throws ApiException {
        Response response = this.inference.ping();
        exchange.getMessage().setBody(response.getStatus());
    }

    private void predictions(Exchange exchange) throws ApiException {
        Message message = exchange.getMessage();
        TorchServeConfiguration configuration = getEndpoint().getConfiguration();
        String modelName = Optional
                .ofNullable(message.getHeader(TorchServeConstants.MODEL_NAME, String.class))
                .orElse(configuration.getModelName());
        String modelVersion = Optional
                .ofNullable(message.getHeader(TorchServeConstants.MODEL_VERSION, String.class))
                .orElse(configuration.getModelVersion());
        Object body = message.getBody(byte[].class);
        Object response;
        if (modelVersion == null) {
            response = this.inference.predictions(modelName, body);
        } else {
            response = this.inference.predictions(modelName, modelVersion, body);
        }
        message.setBody(response);
    }

    private void explanations(Exchange exchange) throws ApiException {
        Message message = exchange.getMessage();
        TorchServeConfiguration configuration = getEndpoint().getConfiguration();
        String modelName = Optional
                .ofNullable(message.getHeader(TorchServeConstants.MODEL_NAME, String.class))
                .orElse(configuration.getModelName());
        Object response = this.inference.explanations(modelName);
        message.setBody(response);
    }
}
