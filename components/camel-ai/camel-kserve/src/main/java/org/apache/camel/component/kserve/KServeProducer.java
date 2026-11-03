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

import com.google.protobuf.GeneratedMessage;
import inference.GRPCInferenceServiceGrpc;
import inference.GrpcPredictV2;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;

public class KServeProducer extends DefaultProducer {

    /** The end of the error of an api that reads its request from the body. */
    private static final String BUILD_THE_REQUEST = ": build the protobuf request before the endpoint";
    /** The end of the error of an api that needs no body. */
    private static final String OR_NO_BODY
            = ", and builds the request from the options and headers when there is no body, or an empty one";

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
    public void process(Exchange exchange) throws Exception {
        GeneratedMessage response = switch (api) {
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

    /**
     * The body as the request of the api. No body, or an empty one, gives null: the request is then built from the
     * options and headers only. Another body that is not the request and does not convert to it, such as text, JSON,
     * bytes or a Map, fails instead of being dropped, as the request would be sent without it.
     */
    private <T> T requestOf(Exchange exchange, Class<T> type, String reads) throws InvalidPayloadException {
        Message message = exchange.getMessage();
        if (isEmpty(message.getBody())) {
            return null;
        }
        T request = message.getBody(type);
        if (request == null) {
            throw new InvalidPayloadException(exchange, type, message, "the " + api + " api reads " + reads);
        }
        return request;
    }

    /**
     * No body, or an empty one that an earlier step can leave, such as a blank String or an empty byte array.
     */
    private static boolean isEmpty(Object body) {
        return body == null
                || (body instanceof String text && text.isBlank())
                || (body instanceof byte[] bytes && bytes.length == 0);
    }

    private GrpcPredictV2.ModelInferResponse infer(Exchange exchange) throws InvalidPayloadException {
        Message message = exchange.getMessage();
        GrpcPredictV2.ModelInferRequest request = requestOf(exchange, GrpcPredictV2.ModelInferRequest.class,
                "a ModelInferRequest with the input tensors, or its Builder" + BUILD_THE_REQUEST);

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

    private GrpcPredictV2.ModelReadyResponse modelReady(Exchange exchange) throws InvalidPayloadException {
        Message message = exchange.getMessage();
        GrpcPredictV2.ModelReadyRequest request = requestOf(exchange, GrpcPredictV2.ModelReadyRequest.class,
                "a ModelReadyRequest or its Builder" + OR_NO_BODY);

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

    private GrpcPredictV2.ModelMetadataResponse modelMetadata(Exchange exchange) throws InvalidPayloadException {
        Message message = exchange.getMessage();
        GrpcPredictV2.ModelMetadataRequest request = requestOf(exchange, GrpcPredictV2.ModelMetadataRequest.class,
                "a ModelMetadataRequest or its Builder" + OR_NO_BODY);

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
