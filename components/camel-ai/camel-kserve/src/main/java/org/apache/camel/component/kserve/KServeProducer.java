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
package org.apache.camel.component.kserve;

import java.util.Optional;

import com.google.protobuf.GeneratedMessageV3;
import inference.GRPCInferenceServiceGrpc;
import inference.GrpcPredictV2;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;

public class KServeProducer extends DefaultProducer {

    private final String api;
    private final GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingStub inferenceService;

    public KServeProducer(KServeEndpoint endpoint) {
        super(endpoint);
        this.api = endpoint.getApi();
        this.inferenceService = endpoint.getInferenceService();
    }

    @Override
    public KServeEndpoint getEndpoint() {
        return (KServeEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) {
        GeneratedMessageV3 response = switch (api) {
            case "infer" -> infer(exchange);
            case "model/ready" -> modelReady(exchange);
            case "model/metadata" -> modelMetadata(exchange);
            case "server/ready" -> serverReady();
            case "server/live" -> serverLive();
            case "server/metadata" -> serverMetadata();
            default -> throw new IllegalArgumentException("Unsupported API: " + api);
        };
        exchange.getMessage().setBody(response);
    }

    private GrpcPredictV2.ModelInferResponse infer(Exchange exchange) {
        Message message = exchange.getMessage();
        GrpcPredictV2.ModelInferRequest request = message.getBody(GrpcPredictV2.ModelInferRequest.class);

        KServeConfiguration configuration = getEndpoint().getConfiguration();
        GrpcPredictV2.ModelInferRequest.Builder builder = GrpcPredictV2.ModelInferRequest.newBuilder();
        Optional.ofNullable(message.getHeader(KServeConstants.MODEL_NAME, String.class))
                .or(() -> Optional.ofNullable(configuration.getModelName()))
                .ifPresent(builder::setModelName);
        Optional.ofNullable(message.getHeader(KServeConstants.MODEL_VERSION, String.class))
                .or(() -> Optional.ofNullable(configuration.getModelVersion()))
                .ifPresent(builder::setModelVersion);
        if (request != null) {
            builder.mergeFrom(request);
        }

        return inferenceService.modelInfer(builder.build());
    }

    private GrpcPredictV2.ModelReadyResponse modelReady(Exchange exchange) {
        Message message = exchange.getMessage();
        GrpcPredictV2.ModelReadyRequest request = message.getBody(GrpcPredictV2.ModelReadyRequest.class);

        KServeConfiguration configuration = getEndpoint().getConfiguration();
        GrpcPredictV2.ModelReadyRequest.Builder builder = GrpcPredictV2.ModelReadyRequest.newBuilder();
        Optional.ofNullable(message.getHeader(KServeConstants.MODEL_NAME, String.class))
                .or(() -> Optional.ofNullable(configuration.getModelName()))
                .ifPresent(builder::setName);
        Optional.ofNullable(message.getHeader(KServeConstants.MODEL_VERSION, String.class))
                .or(() -> Optional.ofNullable(configuration.getModelVersion()))
                .ifPresent(builder::setVersion);
        if (request != null) {
            builder.mergeFrom(request);
        }

        return inferenceService.modelReady(builder.build());
    }

    private GrpcPredictV2.ModelMetadataResponse modelMetadata(Exchange exchange) {
        Message message = exchange.getMessage();
        GrpcPredictV2.ModelMetadataRequest request = message.getBody(GrpcPredictV2.ModelMetadataRequest.class);

        KServeConfiguration configuration = getEndpoint().getConfiguration();
        GrpcPredictV2.ModelMetadataRequest.Builder builder = GrpcPredictV2.ModelMetadataRequest.newBuilder();
        Optional.ofNullable(message.getHeader(KServeConstants.MODEL_NAME, String.class))
                .or(() -> Optional.ofNullable(configuration.getModelName()))
                .ifPresent(builder::setName);
        Optional.ofNullable(message.getHeader(KServeConstants.MODEL_VERSION, String.class))
                .or(() -> Optional.ofNullable(configuration.getModelVersion()))
                .ifPresent(builder::setVersion);
        if (request != null) {
            builder.mergeFrom(request);
        }

        return inferenceService.modelMetadata(builder.build());
    }

    private GrpcPredictV2.ServerReadyResponse serverReady() {
        GrpcPredictV2.ServerReadyRequest.Builder builder = GrpcPredictV2.ServerReadyRequest.newBuilder();
        return inferenceService.serverReady(builder.build());
    }

    private GrpcPredictV2.ServerLiveResponse serverLive() {
        GrpcPredictV2.ServerLiveRequest.Builder builder = GrpcPredictV2.ServerLiveRequest.newBuilder();
        return inferenceService.serverLive(builder.build());
    }

    private GrpcPredictV2.ServerMetadataResponse serverMetadata() {
        GrpcPredictV2.ServerMetadataRequest.Builder builder = GrpcPredictV2.ServerMetadataRequest.newBuilder();
        return inferenceService.serverMetadata(builder.build());
    }
}
