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
package org.apache.camel.component.tensorflow.serving;

import java.util.Optional;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.Int64Value;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import tensorflow.serving.Classification;
import tensorflow.serving.GetModelMetadata;
import tensorflow.serving.GetModelStatus;
import tensorflow.serving.Model;
import tensorflow.serving.ModelServiceGrpc;
import tensorflow.serving.Predict;
import tensorflow.serving.PredictionServiceGrpc;
import tensorflow.serving.RegressionOuterClass;

public class TensorFlowServingProducer extends DefaultProducer {

    /** The end of the error of an api that reads its request from the body. */
    private static final String BUILD_THE_REQUEST = ": build the protobuf request before the endpoint";
    /** The end of the error of an api that needs no body. */
    private static final String OR_NO_BODY
            = ", and builds the request from the options and headers when there is no body, or an empty one";

    private final String api;
    private final ModelServiceGrpc.ModelServiceBlockingStub modelService;
    private final PredictionServiceGrpc.PredictionServiceBlockingStub predictionService;

    public TensorFlowServingProducer(TensorFlowServingEndpoint endpoint) {
        super(endpoint);
        this.api = endpoint.getApi();
        this.modelService = endpoint.getModelService();
        this.predictionService = endpoint.getPredictionService();
    }

    @Override
    public TensorFlowServingEndpoint getEndpoint() {
        return (TensorFlowServingEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        GeneratedMessage response = switch (api) {
            case "model-status" -> modelStatus(exchange);
            case "model-metadata" -> modelMetadata(exchange);
            case "classify" -> classify(exchange);
            case "regress" -> regress(exchange);
            case "predict" -> predict(exchange);
            default -> throw new IllegalArgumentException("Unsupported API: " + api);
        };
        exchange.getMessage().setBody(response);
    }

    private Model.ModelSpec.Builder modelSpec(Exchange exchange) {
        Message message = exchange.getMessage();
        TensorFlowServingConfiguration configuration = getEndpoint().getConfiguration();
        String modelName = Optional
                .ofNullable(message.getHeader(TensorFlowServingConstants.MODEL_NAME, String.class))
                .orElse(configuration.getModelName());
        Long modelVersion = Optional
                .ofNullable(message.getHeader(TensorFlowServingConstants.MODEL_VERSION, Long.class))
                .orElse(configuration.getModelVersion());
        String modelVersionLabel = Optional
                .ofNullable(message.getHeader(TensorFlowServingConstants.MODEL_VERSION_LABEL, String.class))
                .orElse(configuration.getModelVersionLabel());
        String signatureName = Optional
                .ofNullable(message.getHeader(TensorFlowServingConstants.SIGNATURE_NAME, String.class))
                .orElse(configuration.getSignatureName());

        Model.ModelSpec.Builder builder = Model.ModelSpec.newBuilder().setName(modelName);
        if (modelVersion != null) {
            builder.setVersion(Int64Value.of(modelVersion));
        }
        if (modelVersionLabel != null) {
            builder.setVersionLabel(modelVersionLabel);
        }
        if (signatureName != null) {
            builder.setSignatureName(signatureName);
        }
        return builder;
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

    private GetModelStatus.GetModelStatusResponse modelStatus(Exchange exchange) throws InvalidPayloadException {
        GetModelStatus.GetModelStatusRequest request = requestOf(exchange, GetModelStatus.GetModelStatusRequest.class,
                "a GetModelStatusRequest or its Builder" + OR_NO_BODY);
        GetModelStatus.GetModelStatusRequest.Builder builder = GetModelStatus.GetModelStatusRequest.newBuilder()
                .setModelSpec(modelSpec(exchange));
        if (request != null) {
            builder.mergeFrom(request);
        }
        return modelService.getModelStatus(builder.build());
    }

    private GetModelMetadata.GetModelMetadataResponse modelMetadata(Exchange exchange) throws InvalidPayloadException {
        GetModelMetadata.GetModelMetadataRequest request = requestOf(exchange,
                GetModelMetadata.GetModelMetadataRequest.class,
                "a GetModelMetadataRequest or its Builder" + OR_NO_BODY);
        GetModelMetadata.GetModelMetadataRequest.Builder builder = GetModelMetadata.GetModelMetadataRequest.newBuilder()
                .setModelSpec(modelSpec(exchange));
        if (request != null) {
            builder.mergeFrom(request);
        }
        // the metadata fields of the request, or else signature_def, the only field TensorFlow Serving supports
        // (merging appends a repeated field, so adding it before the merge would ask for it twice)
        if (builder.getMetadataFieldCount() == 0) {
            builder.addMetadataField("signature_def");
        }
        return predictionService.getModelMetadata(builder.build());
    }

    private Classification.ClassificationResponse classify(Exchange exchange) throws InvalidPayloadException {
        Classification.ClassificationRequest request = requestOf(exchange, Classification.ClassificationRequest.class,
                "a ClassificationRequest, an Input, or the Builder of one" + BUILD_THE_REQUEST);
        Classification.ClassificationRequest.Builder builder = Classification.ClassificationRequest.newBuilder()
                .setModelSpec(modelSpec(exchange));
        if (request != null) {
            builder.mergeFrom(request);
        }
        return predictionService.classify(builder.build());
    }

    private RegressionOuterClass.RegressionResponse regress(Exchange exchange) throws InvalidPayloadException {
        RegressionOuterClass.RegressionRequest request = requestOf(
                exchange, RegressionOuterClass.RegressionRequest.class,
                "a RegressionRequest, an Input, or the Builder of one" + BUILD_THE_REQUEST);
        RegressionOuterClass.RegressionRequest.Builder builder = RegressionOuterClass.RegressionRequest.newBuilder()
                .setModelSpec(modelSpec(exchange));
        if (request != null) {
            builder.mergeFrom(request);
        }
        return predictionService.regress(builder.build());
    }

    private Predict.PredictResponse predict(Exchange exchange) throws InvalidPayloadException {
        Predict.PredictRequest request = requestOf(exchange, Predict.PredictRequest.class,
                "a PredictRequest or its Builder" + BUILD_THE_REQUEST);
        Predict.PredictRequest.Builder builder = Predict.PredictRequest.newBuilder()
                .setModelSpec(modelSpec(exchange));
        if (request != null) {
            builder.mergeFrom(request);
        }
        return predictionService.predict(builder.build());
    }
}
